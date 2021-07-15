# keycloak-verify-email-by-code

Required action to verify email by code

This [Keycloak](https://www.keycloak.org) plugin adds a required action to verify email by code.

[![Build Status](https://github.com/RedFroggy/keycloak-verify-email-by-code/actions/workflows/tag.yml/badge.svg)](https://github.com/RedFroggy/keycloak-verify-email-by-code)

## Features

* Send code on your email
* Verify email by code

## Compatibility

The version 11.0 of this plugin is compatible with Keycloak `11.0.3` and higher.

## Installation

The plugin installation is simple and can be done without a Keycloak server restart.

* Download the latest release from
  the [releases page](https://github.com/RedFroggy/keycloak-verify-email-by-code/releases)
* Copy the JAR file into the `standalone/deployments` directory in your Keycloak server's root
* Restart Keycloak (optional, hot deployment should work)

You can also clone the Github Repository and install the plugin locally with the following command:

```
$ mvn clean install wildfly:deploy
```

## How to use it

### Requirements

Verify required action is deploy in keycloak. Got to {keycloak url}/auth/admin/master/console/#/server-info/providers.

![server-info_providers](/assets/server-info_providers.png)

### Configuration

Once the installation is complete, the `Verify Email by code` required action appears in "
authentication/required-actions" on your realm. Register and enable "VERIFY_EMAIL_CODE" and disable "VERIFY_EMAIL"
![required-actions-conf](/assets/register-action.png)

![required-actions-conf](/assets/required-actions-conf.png)

Once enabled, you can add the following action on user edit page:

![verify-action-user](/assets/verify-action-user.png)

#### Templates

You can override individual templates in your own theme. To create a custom email message for the mytheme theme copy
themes/base/email/email-verification-with-code.ftl (in keycloak theme) to themes/mytheme/email/

To create a custom email verify form for the mytheme theme copy template
[login-verify-email-code.ftl](src/main/resources/theme-resources/templates/login-verify-email-code.ftl) to
themes/mytheme/login

#### Code length and symbols

Verify email code is generated using `org.keycloak.common.util.RandomString`. By default generated code will be 8 characters long and will use alphanumeric symbols. You may customize this behavior using SPI configuration in your `standalone.xml` file:
- property `codeLength` is a numeric value representing the number of symbols, defaults to `8`
- property `codeSymbols` is a string value listing all accepted symbols, defaults to `RandomString.alphanum`

e.g. configure action to generate a code of 6 digits :
```xml
<subsystem xmlns="urn:jboss:domain:keycloak-server:1.1">
    [...]
    <spi name="required-action">
        <provider name="VERIFY_EMAIL_CODE" enabled="true">
            <properties>
                <property name="codeLength" value="6"/>
                <property name="codeSymbols" value="0123456789"/>
            </properties>
        </provider>
    </spi>
</subsystem>
```

## Q&A

[See Q&A](FAQ.md)

## How to contribute

[See here](CONTRIBUTING.en.md)
