/*
 * Copyright 2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.springframework.jndi.JndiObjectFactoryBean
import org.springframework.mail.javamail.JavaMailSenderImpl
import grails.spring.BeanBuilder

import grails.plugin.mail.*

class MailGrailsPlugin {
    def version = "1.0.8-SNAPSHOT"
    def grailsVersion = "1.3 > *"

    def author = "Grails Plugin Collective"
    def authorEmail = "grails.plugin.collective@gmail.com"
    def title = "Provides Mail support to a running Grails application"
    def description = '''\
This plug-in provides a MailService class as well as configuring the necessary beans within
the Spring ApplicationContext.

It also adds a "sendMail" method to all controller classes. A typical example usage is:

sendMail {
    to "fred@g2one.com","ginger@g2one.com"
    from "john@g2one.com"
    cc "marge@g2one.com", "ed@g2one.com"
    bcc "joe@g2one.com"
    subject "Hello John"
    text "this is some text"
}

'''
    def documentation = "http://grails.org/plugins/mail"

    def license = "APACHE"
    def organization = [ name: "Grails Plugin Collective", url: "http://github.com/gpc" ]
    def developers = [
        [ name: "Craig Andrews", email: "candrews@integralblue.com" ],
        [ name: "Luke Daley", email: "ld@ldaley.com" ],
        [ name: "Peter Ledbrook", email: "pledbrook@vmware.com" ],
        [ name: "Jeff Brown", email: "jbrown@vmware.com" ],
        [ name: "Graeme Rocher", email: "grocher@vmware.com" ],
        [ name: "Marc Palmer", email: "marc@grailsrocks.com" ]
    ]

    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMAIL" ]
    def scm = [ url: "http://github.com/gpc/grails-mail" ]

    def observe = ['controllers','services']

    Integer mailConfigHash
    ConfigObject mailConfig
    boolean createdSession = false

    def doWithSpring = {
        mailConfig = application.config.grails.mail
        mailConfigHash = mailConfig.hashCode()

        configureMailSender(delegate, mailConfig)

        mailMessageBuilderFactory(MailMessageBuilderFactory) {
            it.autowire = true
        }

        mailMessageContentRenderer(MailMessageContentRenderer) {
            it.autowire = true
        }
    }

    def doWithApplicationContext = { applicationContext ->
        configureSendMail(application, applicationContext)
    }

    def onChange = { event ->
        configureSendMail(event.application, event.ctx)
    }

    def onConfigChange = { event ->
        ConfigObject newMailConfig = event.source.grails.mail
        Integer newMailConfigHash = newMailConfig.hashCode()

        if (newMailConfigHash != mailConfigHash) {
            if (createdSession) {
                event.ctx.removeBeanDefinition("mailSession")
            }

            event.ctx.removeBeanDefinition("mailSender")

            mailConfig = newMailConfig
            mailConfigHash = newMailConfigHash

			event.ctx.getBean(MailService.class).setPoolSize(mailConfig.poolSize?:null)

            new BeanBuilder().beans {
                configureMailSender(delegate, mailConfig)
            }.registerBeans(event.ctx)

            event.ctx.getBean('mailMessageBuilderFactory').mailSender = event.ctx.getBean('mailSender')
        }
    }

    def configureMailSender(builder, config) {
        builder.with {
            if (config.jndiName && !springConfig.containsBean("mailSession")) {
                mailSession(JndiObjectFactoryBean) {
                    jndiName = config.jndiName
                }
                createdSession = true
            } else {
                createdSession = false
            }

            mailSender(JavaMailSenderImpl) {
                if (config.host) {
                    host = config.host
                } else if (!config.jndiName) {
                    def envHost = System.getenv()['SMTP_HOST']
                    if (envHost) {
                        host = envHost
                    } else {
                        host = "localhost"
                    }
                }

                if (config.encoding) {
                    defaultEncoding = config.encoding
                } else if (!config.jndiName) {
                    defaultEncoding = "utf-8"
                }

                if (config.jndiName)
                    session = ref('mailSession')
                if (config.port)
                    port = config.port
                if (config.username)
                    username = config.username
                if (config.password)
                    password = config.password
                if (config.protocol)
                    protocol = config.protocol
                if (config.props instanceof Map && config.props)
                    javaMailProperties = config.props
            }
        }
    }

    def configureSendMail(application, applicationContext) {
        //adding sendMail to controllers
        for (controllerClass in application.controllerClasses) {
            controllerClass.metaClass.sendMail = { Closure dsl ->
                applicationContext.mailService.sendMail(dsl)
            }
        }

        String mailServiceClassName = applicationContext.mailService.metaClass.theClass.name

        //adding sendMail to all services, besides the mailService of the plugin
        for (serviceClass in application.serviceClasses) {
            if (serviceClass.clazz.name != mailServiceClassName) {
                serviceClass.metaClass.sendMail = { Closure dsl ->
                    applicationContext.mailService.sendMail(dsl)
                }
            }
        }
    }
}
