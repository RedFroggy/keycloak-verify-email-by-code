<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.exists('email_code'); section>
    <#if section = "form">
        <form id="kc-verify-email-code-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!} ${messagesPerField.printIfExists('email_code',properties.kcFormGroupErrorClass!)}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="email_code" class="${properties.kcLabelClass!}">${msg("verifyEmailCode")}</label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <input type="text" id="email_code" name="email_code" class="${properties.kcInputClass!}"
                           autocomplete="off"
                           autofocus
                           aria-invalid="<#if messagesPerField.exists('email_code')>true</#if>"
                    />

                    <#if messagesPerField.exists('email_code')>
                        <span id="input-error-email_code" class="${properties.kcInputErrorMessageClass!}"
                              aria-live="polite">
                                ${kcSanitize(messagesPerField.get('email_code'))?no_esc}
                            </span>
                    </#if>
                </div>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                           type="submit" name="action" value="verify" />
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                           type="submit" name="action" value="resend" />
                </div>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-options" class="${properties.kcFormOptionsClass!}">
                    <div class="${properties.kcFormOptionsWrapperClass!}">
                        <a href="${url.loginRestartFlowUrl}">${msg("doCancel")}</a>
                    </div>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>