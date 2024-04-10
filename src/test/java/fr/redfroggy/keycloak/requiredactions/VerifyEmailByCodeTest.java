package fr.redfroggy.keycloak.requiredactions;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.email.freemarker.beans.ProfileBean;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.userprofile.UserProfileProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static fr.redfroggy.keycloak.requiredactions.VerifyEmailByCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class})
class VerifyEmailByCodeTest {

    private final VerifyEmailByCode action = new VerifyEmailByCode();
    @Mock
    private RealmModel realm;
    @Mock
    private UserModel user;
    @Mock
    private UserProfileProvider provider;
    @Mock
    private KeycloakSession session;
    @Mock
    private EventBuilder event;
    @Mock
    private AuthenticationSessionModel authSession;
    @Mock
    private LoginFormsProvider form;
    @Mock
    private EmailTemplateProvider templateProvider;
    @Mock
    private Response response;
    @Mock
    private RequiredActionContext requiredActionContext;
    @Mock
    private Config.Scope config;

    @Test
    public void shouldReturnGetId() {
        assertThat(action.getId()).isEqualTo("VERIFY_EMAIL_CODE");
    }

    @Test
    public void shouldTriggerActionWhenRealmIsEnableAndUserNotVerified() {
        initAction();
        when(requiredActionContext.getRealm()).thenReturn(realm);
        when(requiredActionContext.getUser()).thenReturn(user);

        when(realm.isVerifyEmail()).thenReturn(true);
        when(user.isEmailVerified()).thenReturn(false);
        action.evaluateTriggers(requiredActionContext);
        verify(user).addRequiredAction(VERIFY_EMAIL_CODE);
    }

    @Test
    public void shouldNotTriggerActionWhenRealmIsDisable() {
        initAction();
        when(requiredActionContext.getRealm()).thenReturn(realm);
        when(realm.isVerifyEmail()).thenReturn(false);

        action.evaluateTriggers(requiredActionContext);
        verify(user, never()).addRequiredAction(VERIFY_EMAIL_CODE);
    }

    @Test
    public void shouldNotTriggerActionWhenRealmIsEnableAndUserIsVerified() {
        initAction();
        when(requiredActionContext.getRealm()).thenReturn(realm);
        when(requiredActionContext.getUser()).thenReturn(user);

        when(realm.isVerifyEmail()).thenReturn(true);
        when(user.isEmailVerified()).thenReturn(true);
        action.evaluateTriggers(requiredActionContext);
        verify(user, never()).addRequiredAction(VERIFY_EMAIL_CODE);
    }

    @Test
    public void shouldReturnSuccessOnChallengeWhenEmailIsVerified() {
        initAction();
        when(requiredActionContext.getUser()).thenReturn(user);
        when(requiredActionContext.getAuthenticationSession()).thenReturn(authSession);

        when(user.isEmailVerified()).thenReturn(true);
        action.requiredActionChallenge(requiredActionContext);

        verify(requiredActionContext).success();
        verify(authSession).removeAuthNote(VERIFY_EMAIL_CODE);
    }

    @Test
    public void shouldIgnoreChallengeWhenEmailIsBlank() {
        initAction();
        when(requiredActionContext.getUser()).thenReturn(user);

        action.requiredActionChallenge(requiredActionContext);

        verify(requiredActionContext).ignore();
    }

    @Test
    public void shouldLogEmailSendFailedWhenEmailExceptionOnSend() throws EmailException {
        initAction();
        mockChallenge();
        doThrow(EmailException.class).when(templateProvider)
                .send(eq("emailVerificationSubject"), eq("email-verification-with-code.ftl"), any());

        action.requiredActionChallenge(requiredActionContext);

        verify(templateProvider).send(eq("emailVerificationSubject"), eq("email-verification-with-code.ftl"), any());
        verify(event, never()).success();
        verify(form).setError(Errors.EMAIL_SEND_FAILED);
        verify(requiredActionContext).challenge(response);
    }

    @Test
    public void shouldSendVerifyEmailOnChallengeWhenEmailIsNotBlankAndNotVerified() throws EmailException {
        initAction();
        mockChallenge();
        action.requiredActionChallenge(requiredActionContext);

        verify(templateProvider).send(eq("emailVerificationSubject"), eq("email-verification-with-code.ftl"), any());
        verify(event).success();
        verify(form).createForm("login-verify-email-code.ftl");
        verify(requiredActionContext).challenge(response);
    }

    @Test
    public void shouldGenerateCodeWithDefaultConfiguration() {
        initAction();
        mockChallenge();
        action.requiredActionChallenge(requiredActionContext);

        verifyCode(DEFAULT_CODE_LENGTH, DEFAULT_CODE_SYMBOLS);
    }

    @Test
    public void shouldGenerateCodeWithSixDigits() {
        int codeLength = 6;
        String codeSymbols = "0123456789";

        initAction(codeLength, codeSymbols);
        mockChallenge();
        action.requiredActionChallenge(requiredActionContext);

        verifyCode(codeLength, codeSymbols);
    }

    private void mockChallenge() {
        when(requiredActionContext.getUser()).thenReturn(user);
        when(user.getEmail()).thenReturn("keycloak@redfroggy.fr");
        when(session.getProvider(UserProfileProvider.class)).thenReturn(provider);
        when(requiredActionContext.getSession()).thenReturn(session);

        when(requiredActionContext.getEvent()).thenReturn(event);
        when(event.clone()).thenReturn(event);
        when(event.event(EventType.SEND_VERIFY_EMAIL)).thenReturn(event);
        when(event.detail(Details.EMAIL, user.getEmail())).thenReturn(event);

        when(requiredActionContext.getAuthenticationSession()).thenReturn(authSession);

        KeycloakContext keycloakContext = mock(KeycloakContext.class);
        when(session.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getRealm()).thenReturn(realm);

        when(requiredActionContext.form()).thenReturn(form);

        when(session.getProvider(EmailTemplateProvider.class)).thenReturn(templateProvider);
        when(templateProvider.setAuthenticationSession(authSession)).thenReturn(templateProvider);
        when(templateProvider.setRealm(realm)).thenReturn(templateProvider);
        when(templateProvider.setUser(user)).thenReturn(templateProvider);

        when(form.setAttribute(eq("user"), any(ProfileBean.class))).thenReturn(form);
        when(form.createForm(LOGIN_VERIFY_EMAIL_CODE_TEMPLATE)).thenReturn(response);
    }

    private void initAction(int codeLength, String codeSymbols) {
        when(config.getInt(CONFIG_CODE_LENGTH, DEFAULT_CODE_LENGTH)).thenReturn(codeLength);
        when(config.get(CONFIG_CODE_SYMBOLS, DEFAULT_CODE_SYMBOLS)).thenReturn(codeSymbols);
        action.init(config);
    }

    private void initAction() {
        initAction(DEFAULT_CODE_LENGTH, DEFAULT_CODE_SYMBOLS);
    }

    private void verifyCode(int codeLength, String codeSymbols) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(authSession).setAuthNote(eq(VerifyEmailByCode.VERIFY_EMAIL_CODE), code.capture());
        assertThat(code.getValue()).matches("^[" + codeSymbols + "]{" + codeLength + "}$");
    }

    @Test
    public void shouldChallengeOnProcessActionWhenCodeIsNull() throws EmailException {
        initAction();
        when(requiredActionContext.getUser()).thenReturn(user);
        when(user.getEmail()).thenReturn("keycloak@redfroggy.fr");

        when(requiredActionContext.getEvent()).thenReturn(event);
        when(event.clone()).thenReturn(event);
        when(event.event(EventType.VERIFY_EMAIL)).thenReturn(event);
        when(event.detail(Details.EMAIL, user.getEmail())).thenReturn(event);

        when(requiredActionContext.getAuthenticationSession()).thenReturn(authSession);

        mockChallenge();

        action.processAction(requiredActionContext);

        verify(templateProvider).send(eq("emailVerificationSubject"), eq("email-verification-with-code.ftl"), any());
        verify(event).success();
        verify(form).createForm("login-verify-email-code.ftl");
        verify(requiredActionContext).challenge(response);
    }

    @Test
    public void shouldChallengeWithErrorOnProcessActionWhenCodeIsNotValid() {
        initAction();
        when(requiredActionContext.getUser()).thenReturn(user);
        when(session.getProvider(UserProfileProvider.class)).thenReturn(provider);
        when(requiredActionContext.getSession()).thenReturn(session);
        when(user.getEmail()).thenReturn("keycloak@redfroggy.fr");

        when(requiredActionContext.getEvent()).thenReturn(event);
        when(event.clone()).thenReturn(event);
        when(event.event(EventType.VERIFY_EMAIL)).thenReturn(event);
        when(event.detail(Details.EMAIL, user.getEmail())).thenReturn(event);

        when(requiredActionContext.getAuthenticationSession()).thenReturn(authSession);

        when(authSession.getAuthNote(VerifyEmailByCode.VERIFY_EMAIL_CODE)).thenReturn("code is valid");

        HttpRequest request = mock(HttpRequest.class);
        when(requiredActionContext.getHttpRequest()).thenReturn(request);

        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("email_code", "code is not same");
        when(request.getDecodedFormParameters()).thenReturn(params);

        when(requiredActionContext.form()).thenReturn(form);
        when(form.addError(any())).thenReturn(form);
        when(form.setAttribute(eq("user"), any(ProfileBean.class))).thenReturn(form);
        when(form.createForm(LOGIN_VERIFY_EMAIL_CODE_TEMPLATE)).thenReturn(response);

        action.processAction(requiredActionContext);

        verify(requiredActionContext).challenge(any());
        verify(event).error("VerifyEmailInvalidCode");
        verify(user, never()).setEmailVerified(true);
        verify(authSession, never()).removeAuthNote(VerifyEmailByCode.VERIFY_EMAIL_CODE);
        verify(event, never()).success();
        verify(requiredActionContext, never()).success();
    }

    @Test
    public void shouldSuccessOnProcessActionWhenCodeIsValid() {
        initAction();
        when(requiredActionContext.getUser()).thenReturn(user);
        when(user.getEmail()).thenReturn("keycloak@redfroggy.fr");

        when(requiredActionContext.getEvent()).thenReturn(event);
        when(event.clone()).thenReturn(event);
        when(event.event(EventType.VERIFY_EMAIL)).thenReturn(event);
        when(event.detail(Details.EMAIL, user.getEmail())).thenReturn(event);

        when(requiredActionContext.getAuthenticationSession()).thenReturn(authSession);
        when(authSession.getAuthNote(VerifyEmailByCode.VERIFY_EMAIL_CODE)).thenReturn("code is valid");

        HttpRequest request = mock(HttpRequest.class);
        when(requiredActionContext.getHttpRequest()).thenReturn(request);

        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("email_code", "code is valid");
        when(request.getDecodedFormParameters()).thenReturn(params);

        action.processAction(requiredActionContext);

        verify(user).setEmailVerified(true);
        verify(authSession).removeAuthNote(VerifyEmailByCode.VERIFY_EMAIL_CODE);
        verify(event).success();
        verify(requiredActionContext).success();
    }
}
