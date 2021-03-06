package com.echoed.chamber.services.email

import com.echoed.chamber.services.{EchoedException, MessageResponse => MR, Message}

sealed trait EmailMessage extends Message

sealed case class EmailException(message: String = "", cause: Throwable = null) extends EchoedException(message, cause)

import com.echoed.chamber.services.email.{EmailMessage => EM}
import com.echoed.chamber.services.email.{EmailException => EE}


case class SendEmail(recipient: String, subject: String, view: String, model: Map[String, AnyRef]) extends EM
case class SendEmailResponse(
        message: SendEmail,
        value: Either[EE, Boolean]) extends EM with MR[Boolean, SendEmail, EE]

