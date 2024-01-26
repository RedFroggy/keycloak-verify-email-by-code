# keycloak-verify-email-by-code

Required action to verify email by code

This [Keycloak](https://www.keycloak.org) plugin adds a required action to verify email by code.

[![Build Status](https://github.com/RedFroggy/keycloak-verify-email-by-code/actions/workflows/tag.yml/badge.svg)](https://github.com/RedFroggy/keycloak-verify-email-by-code)

## Features

* Send code on your email
* Verify email by code

## Compatibility

The version 23.0.x of this plugin is compatible with Keycloak `23.0.x` and higher.

## How to install?

Download a release (*.jar file) that works with your Keycloak version from
the [list of releases](https://github.com/RedFroggy/keycloak-verify-email-by-code/releases).

### Server

Copy the jar to the `providers` folder and execute the following command:

```shell
${kc.home.dir}/bin/kc.sh build
```

### Maven

You can also clone the Github Repository and install the plugin locally.

### Container image (Docker)

For Docker-based setups mount or copy the jar to `/opt/keycloak/providers`.

You may want to check [docker-compose.yml](docker-compose.yml) as an example.

## How to use it

### Requirements

Verify required action is deploy in keycloak. Got to {keycloak url}/admin/master/console/#/master/providers.

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

- property `code-length` is a numeric value representing the number of symbols, defaults to `8`
- property `code-symbols` is a string value listing all accepted symbols, defaults to `RandomString.alphanum`

e.g. configure action to generate a code of 6 digits :

```shell
bin/kc.[sh|bat] start --spi-required-action-VERIFY_EMAIL_CODE-code-length=6 --spi-required-action-VERIFY_EMAIL_CODE-code-symbols=0123456789 
```

## Q&A

[See Q&A](FAQ.md)

## How to contribute

[See here](CONTRIBUTING.en.md)
