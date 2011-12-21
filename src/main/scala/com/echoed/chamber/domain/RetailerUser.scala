package com.echoed.chamber.domain

import java.util.{UUID, Date}
import java.security.MessageDigest
import java.nio.charset.Charset
import org.apache.commons.codec.binary.Base64


case class RetailerUser(
        id: String,
        updatedOn: Date,
        createdOn: Date,
        retailerId: String,
        name: String,
        email: String,
        salt: String,
        password: String) {

    def this(retailerId: String, name: String, email: String) = this(
        UUID.randomUUID.toString,
        new Date,
        new Date,
        retailerId,
        name,
        email,
        UUID.randomUUID.toString + UUID.randomUUID.toString + UUID.randomUUID.toString + UUID.randomUUID.toString,
        null)


    def isPassword(plainTextPassword: String) = {
        password == hash(plainTextPassword)
    }

    def createPassword(plainTextPassword: String) = {
        this.copy(password = hash(plainTextPassword))
    }

    private def hash(input: String) = {
        val charset = Charset.forName("UTF-8")
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update("108hqdvn9piqw3rfhy2e908bhb2p9834ufh9qw8fvh291348fhc9wqgu2hwq9s8ufy210r".getBytes(charset))
        messageDigest.update(input.getBytes(charset))
        messageDigest.update(salt.getBytes(charset))
        Base64.encodeBase64URLSafeString(messageDigest.digest())
    }
}
