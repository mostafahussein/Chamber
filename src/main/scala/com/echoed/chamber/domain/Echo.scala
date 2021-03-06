package com.echoed.chamber.domain

import java.util.Date
import collection.mutable.ArrayBuilder
import org.apache.commons.codec.binary.Base64
import com.echoed.util.{UUID, ObjectUtils}


case class Echo(
        id: String,
        updatedOn: Date,
        createdOn: Date,
        partnerId: String,
        echoedUserId: String,
        facebookPostId: String,
        twitterStatusId: String,
        @deprecated echoPossibilityId: String,
        partnerSettingsId: String,
        echoMetricsId: String,
        echoClickId: String,
        step: String,
        browserId: String,
        ipAddress: String,
        userAgent: String,
        referrerUrl: String,
        view: String,
        order: Order,
        product: Product,
        image: Image) {


    this.ensuring(step != null, "Step cannot be null")

    val isEchoed = echoedUserId != null

    def asUrlParams(prefix: String = "", encode: Boolean = false) = ObjectUtils.asUrlParams(this, prefix, encode)

    def asMap = ObjectUtils.asMap(this)

    val productId = product.productId
    val productName = product.productName
    val price = product.price
    val landingPageUrl = product.landingPageUrl
    val brand = product.brand

    val orderId = order.orderId
    val customerId = order.customerId
    val boughtOn = order.boughtOn

    val imageUrl = image.preferredUrl
    val imageWidth = image.preferredWidth
    val imageHeight = image.preferredHeight
}


object Echo {

    def make(
            partnerId: String,
            customerId: String,
            productId: String,
            boughtOn: Date,
            step: String,
            orderId: String,
            price: Float,
            imageUrl: String,
            landingPageUrl: String,
            productName: String,
            category: String,
            brand: String,
            description: String,
            echoClickId:String,
            browserId: String,
            ipAddress: String,
            userAgent: String,
            referrerUrl: String,
            partnerSettingsId: String = null,
            view: String = null) = {

        val id = UUID()
        val date = new Date

        //NOTE: do not include any changing attributes in the hash calc.  For example, step should never
        //be included as it changes with every step the user takes to echo a purchase (button, login, etc)
        val echoPossibilityId = (for {
                e <- Option("UTF-8")
                r <- Option(partnerId)
                c <- Option(customerId)
                p <- Option(productId)
                o <- Option(orderId)
            } yield {
                val arrayBuilder = ArrayBuilder.make[Byte]
                arrayBuilder ++= r.getBytes(e)
                arrayBuilder ++= c.getBytes(e)
                arrayBuilder ++= p.getBytes(e)
                arrayBuilder ++= o.getBytes(e)
                Base64.encodeBase64URLSafeString(arrayBuilder.result())
            }).orNull

        Echo(
            id = id,
            updatedOn = date,
            createdOn = date,
            partnerId = partnerId,
            echoedUserId = null,
            facebookPostId = null,
            twitterStatusId = null,
            echoPossibilityId = echoPossibilityId,
            partnerSettingsId = partnerSettingsId,
            echoMetricsId = null,
            echoClickId = echoClickId,
            step = step,
            browserId = browserId,
            ipAddress = ipAddress,
            userAgent = Option(userAgent).map(_.take(254)).orNull,
            referrerUrl = referrerUrl,
            view = view,
            order = Order(id, date, date, customerId, boughtOn, orderId),
            product = Product(
                        id,
                        date,
                        date,
                        productId,
                        price,
                        landingPageUrl,
                        productName,
                        category,
                        brand,
                        Option(description).map(_.take(1023)).orNull),
            image = Image(imageUrl))
    }
}



