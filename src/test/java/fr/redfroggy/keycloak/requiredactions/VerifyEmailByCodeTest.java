package fr.redfroggy.keycloak.requiredactions;

import org.jboss.resteasy.spi.HttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import static fr.redfroggy.keycloak.requiredactions.VerifyEmailByCode.LOGIN_VERIFY_EMAIL_CODE_TEMPLATE;
import static fr.redfroggy.keycloak.requiredactions.VerifyEmailByCode.VERIFY_EMAIL_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class})
class VerifyEmailByCodeTest {

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

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

    private VerifyEmailByCode action = new VerifyEmailByCode();


    @Test
    public void shouldReturnThisWhenCreate() {
        assertThat(action.create(session)).isEqualTo(action);
    }

    @Test
    public void shouldReturnGetId() {
        assertThat(action.getId()).isEqualTo("VERIFY_EMAIL_CODE");
    }

    @Test
    public void shouldTriggerActionWhenRealmIsEnableAndUserNotVerified() {
        when(requiredActionContext.getRealm()).thenReturn(realm);
        when(requiredActionContext.getUser()).thenReturn(user);

        when(realm.isVerifyEmail()).thenReturn(true);
        when(user.isEmailVerified()).thenReturn(false);
        action.evaluateTriggers(requiredActionContext);
        verify(user).addRequiredAction(VERIFY_EMAIL_CODE);
    }

    @Test
    public void shouldNotTriggerActionWhenRealmIsDisable() {
        when(requiredActionContext.getRealm()).thenReturn(realm);
        when(realm.isVerifyEmail()).thenReturn(false);

        action.evaluateTriggers(requiredActionContext);
        verify(user, never()).addRequiredAction(VERIFY_EMAIL_CODE);
    }

    @Test
    public void shouldNotTriggerActionWhenRealmIsEnableAndUserIsVerified() {
        when(requiredActionContext.getRealm()).thenReturn(realm);
        when(requiredActionContext.getUser()).thenReturn(user);

        when(realm.isVerifyEmail()).thenReturn(true);
        when(user.isEmailVerified()).thenReturn(true);
        action.evaluateTriggers(requiredActionContext);
        verify(user, never()).addRequiredAction(VERIFY_EMAIL_CODE);
    }

    @Test
    public void shouldReturnSuccessOnChallengeWhenEmailIsVerified() {
        when(requiredActionContext.getUser()).thenReturn(user);

        when(user.isEmailVerified()).thenReturn(true);
        action.requiredActionChallenge(requiredActionContext);

        verify(requiredActionContext).success();
    }

    @Test
    public void shouldIgnoreChallengeWhenEmailIsBlank() {
        when(requiredActionContext.getUser()).thenReturn(user);

        action.requiredActionChallenge(requiredActionContext);

        verify(requiredActionContext).ignore();
    }

    @Test
    public void shouldLogEmailSendFailedWhenEmailExceptionOnSend() throws EmailException {
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
        mockChallenge();
        action.requiredActionChallenge(requiredActionContext);

        verify(templateProvider).send(eq("emailVerificationSubject"), eq("email-verification-with-code.ftl"), any());
        verify(event).success();
        verify(form).createForm("login-verify-email-code.ftl");
        verify(requiredActionContext).challenge(response);
    }

    private void mockChallenge() {
        when(requiredActionContext.getUser()).thenReturn(user);
        when(user.getEmail()).thenReturn("keycloak@redfroggy.fr");

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

        when(form.createForm(LOGIN_VERIFY_EMAIL_CODE_TEMPLATE)).thenReturn(response);
    }

    @Test
    public void shouldChallengeOnProcessActionWhenCodeIsNull() throws EmailException {
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
    public void shouldChallengeWithErrorOnProcessActionWhenCodeIsNotValid() throws EmailException {
        when(requiredActionContext.getUser()).thenReturn(user);
        when(user.getEmail()).thenReturn("keycloak@redfroggy.fr");

        when(requiredActionContext.getEvent()).thenReturn(event);
        when(event.clone()).thenReturn(event);
        when(event.event(EventType.VERIFY_EMAIL)).thenReturn(event);
        when(event.detail(Details.EMAIL, user.getEmail())).thenReturn(event);

        when(requiredActionContext.getAuthenticationSession()).thenReturn(authSession);
        when(authSession.getAuthNote(Constants.VERIFY_EMAIL_CODE)).thenReturn("code is valid");

        HttpRequest request = mock(HttpRequest.class);
        when(requiredActionContext.getHttpRequest()).thenReturn(request);

        MultivaluedMap params = new MultivaluedHashMap();
        params.add("email_code", "code is not same");
        when(request.getDecodedFormParameters()).thenReturn(params);

        when(requiredActionContext.form()).thenReturn(form);
        when(form.addError(any())).thenReturn(form);
        when(form.createForm(LOGIN_VERIFY_EMAIL_CODE_TEMPLATE)).thenReturn(response);

        action.processAction(requiredActionContext);

        verify(requiredActionContext).challenge(any());
        verify(event).error("VerifyEmailInvalidCode");
        verify(user, never()).setEmailVerified(true);
        verify(authSession, never()).removeAuthNote(Constants.VERIFY_EMAIL_CODE);
        verify(event, never()).success();
        verify(requiredActionContext, never()).success();
    }

    @Test
    public void shouldSuccessOnProcessActionWhenCodeIsValid() throws EmailException {
        when(requiredActionContext.getUser()).thenReturn(user);
        when(user.getEmail()).thenReturn("keycloak@redfroggy.fr");

        when(requiredActionContext.getEvent()).thenReturn(event);
        when(event.clone()).thenReturn(event);
        when(event.event(EventType.VERIFY_EMAIL)).thenReturn(event);
        when(event.detail(Details.EMAIL, user.getEmail())).thenReturn(event);

        when(requiredActionContext.getAuthenticationSession()).thenReturn(authSession);
        when(authSession.getAuthNote(Constants.VERIFY_EMAIL_CODE)).thenReturn("code is valid");

        HttpRequest request = mock(HttpRequest.class);
        when(requiredActionContext.getHttpRequest()).thenReturn(request);

        MultivaluedMap params = new MultivaluedHashMap();
        params.add("email_code", "code is valid");
        when(request.getDecodedFormParameters()).thenReturn(params);

        action.processAction(requiredActionContext);

        verify(user).setEmailVerified(true);
        verify(authSession).removeAuthNote(Constants.VERIFY_EMAIL_CODE);
        verify(event).success();
        verify(requiredActionContext).success();
    }
}