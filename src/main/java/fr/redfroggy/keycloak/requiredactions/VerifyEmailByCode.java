/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.redfroggy.keycloak.requiredactions;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilderException;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.common.util.Time;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.email.freemarker.beans.ProfileBean;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ServerInfoAwareProviderFactory;
import org.keycloak.services.validation.Validation;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class VerifyEmailByCode implements RequiredActionProvider, RequiredActionFactory, ServerInfoAwareProviderFactory {
    public static final String VERIFY_EMAIL_CODE = "VERIFY_EMAIL_CODE";
    public static final String EMAIL_CODE = "email_code";
    public static final String INVALID_CODE = "VerifyEmailInvalidCode";
    public static final String LOGIN_VERIFY_EMAIL_CODE_TEMPLATE = "login-verify-email-code.ftl";
    public static final String CONFIG_CODE_LENGTH = "code-length";
    public static final String CONFIG_CODE_SYMBOLS = "code-symbols";
    public static final int DEFAULT_CODE_LENGTH = 8;
    private static final String LAST_OTP_SENT = "last_otp_sent";
    private static final String OTP_SENT = "otp_sent";
    private static final long DEBOUNCE_SECONDS = 30;
    public static final String DEFAULT_CODE_SYMBOLS = String.valueOf(SecretGenerator.ALPHANUM);
    private static final Logger logger = Logger.getLogger(VerifyEmailByCode.class);
    private int codeLength;
    private String codeSymbols;

    private static void createFormChallenge(RequiredActionContext context, FormMessage errorMessage) {
        LoginFormsProvider loginFormsProvider = context.form();
        if (Objects.nonNull(errorMessage)) {
            loginFormsProvider = loginFormsProvider.addError(errorMessage);
        }
        Response challenge = loginFormsProvider
                .setAttribute("user", new ProfileBean(context.getUser(), context.getSession()))
                .createForm(LOGIN_VERIFY_EMAIL_CODE_TEMPLATE);
        context.challenge(challenge);
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        if (context.getRealm().isVerifyEmail()
                && !context.getUser().isEmailVerified()) {
            context.getUser().addRequiredAction(VERIFY_EMAIL_CODE);
            context.getAuthenticationSession().removeAuthNote(LAST_OTP_SENT);
            context.getAuthenticationSession().removeAuthNote(OTP_SENT);
            logger.debug("User is required to verify email");
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        if (context.getUser().isEmailVerified()) {
            context.getAuthenticationSession().removeAuthNote(VERIFY_EMAIL_CODE);
            context.success();
            return;
        }

        String email = context.getUser().getEmail();
        if (Validation.isBlank(email)) {
            context.ignore();
            return;
        }

        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        if (authSession.getAuthNote(OTP_SENT) == null && canSendOtp(authSession)) {
            sendVerifyEmailAndCreateForm(context);
            authSession.setAuthNote(OTP_SENT, "true");
            logger.info("Initial OTP sent for user " + context.getUser().getUsername());
        } else if (!canSendOtp(authSession)) {
            long secondsRemaining = getSecondsRemaining(authSession);
            createFormChallenge(context, new FormMessage(null, "error.debounce", String.valueOf(secondsRemaining)));
        } else {
            createFormChallenge(context, null);
        }
    }

    @Override
    public void processAction(RequiredActionContext context) {
        EventBuilder event = context.getEvent().clone().event(EventType.VERIFY_EMAIL).detail(Details.EMAIL,
                context.getUser().getEmail());
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String action = formData.getFirst("action");

        if ("resend".equals(action)) {
            if (canSendOtp(authSession)) {
                sendVerifyEmailAndCreateForm(context);
                authSession.setAuthNote(OTP_SENT, "true");
                logger.info("OTP resent for user " + context.getUser().getUsername());
                event.success();
            } else {
                long secondsRemaining = getSecondsRemaining(authSession);
                createFormChallenge(context, new FormMessage(null, "error.debounce", String.valueOf(secondsRemaining)));
                logger.info("Resend rejected for user " + context.getUser().getUsername() +
                        " (" + secondsRemaining + "s remaining)");
                event.error("debounce_rejected");
            }
        } else {
            String code = formData.getFirst(EMAIL_CODE);
            String storedCode = authSession.getAuthNote(VERIFY_EMAIL_CODE);
            if (code == null || !code.equals(storedCode)) {
                createFormChallenge(context, new FormMessage(EMAIL_CODE, INVALID_CODE));
                event.error(INVALID_CODE);
            } else {
                context.getUser().setEmailVerified(true);
                authSession.removeAuthNote(VERIFY_EMAIL_CODE);
                authSession.removeAuthNote(LAST_OTP_SENT);
                authSession.removeAuthNote(OTP_SENT);
                event.success();
                context.success();
            }
        }
    }

    private boolean canSendOtp(AuthenticationSessionModel authSession) {
        String lastSent = authSession.getAuthNote(LAST_OTP_SENT);
        long currentTime = Time.currentTime();
        long lastTime = lastSent != null ? Long.parseLong(lastSent) : 0;
        return currentTime - lastTime >= DEBOUNCE_SECONDS;
    }

    private long getSecondsRemaining(AuthenticationSessionModel authSession) {
        String lastSent = authSession.getAuthNote(LAST_OTP_SENT);
        long lastTime = lastSent != null ? Long.parseLong(lastSent) : 0;
        return lastTime > 0 ? DEBOUNCE_SECONDS - (Time.currentTime() - lastTime) : 0;
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {
        codeLength = config.getInt(CONFIG_CODE_LENGTH, DEFAULT_CODE_LENGTH);
        codeSymbols = config.get(CONFIG_CODE_SYMBOLS, DEFAULT_CODE_SYMBOLS);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return VERIFY_EMAIL_CODE;
    }

    private void sendVerifyEmailAndCreateForm(RequiredActionContext context) throws UriBuilderException, IllegalArgumentException {
        KeycloakSession session = context.getSession();
        UserModel user = context.getUser();
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        EventBuilder event = context.getEvent().clone().event(EventType.SEND_VERIFY_EMAIL).detail(Details.EMAIL, user.getEmail());
        String code = SecretGenerator.getInstance().randomString(codeLength, codeSymbols.toCharArray());
        authSession.setAuthNote(VERIFY_EMAIL_CODE, code);
        authSession.setAuthNote(LAST_OTP_SENT, String.valueOf(Time.currentTime()));
        RealmModel realm = session.getContext().getRealm();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("code", code);

        LoginFormsProvider form = context.form();
        try {
            session
                    .getProvider(EmailTemplateProvider.class)
                    .setAuthenticationSession(authSession)
                    .setRealm(realm)
                    .setUser(user)
                    .send("emailVerificationSubject", "email-verification-with-code.ftl", attributes);
            event.success();
        } catch (EmailException e) {
            logger.error("Failed to send verification email", e);
            event.error(Errors.EMAIL_SEND_FAILED);
            form.setError(Errors.EMAIL_SEND_FAILED);
        }

        createFormChallenge(context, null);
    }

    @Override
    public String getDisplayText() {
        logger.info("Retrieved display text for VerifyEmailByCode");
        return "Verify Email by code";
    }

    @Override
    public Map<String, String> getOperationalInfo() {
        Map<String, String> ret = new LinkedHashMap<>();
        ret.put(VERIFY_EMAIL_CODE + "." + CONFIG_CODE_LENGTH, String.valueOf(codeLength));
        ret.put(VERIFY_EMAIL_CODE + "." + CONFIG_CODE_SYMBOLS, codeSymbols);
        return ret;
    }
}
