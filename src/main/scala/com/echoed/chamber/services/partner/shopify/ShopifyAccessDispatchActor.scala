package com.echoed.chamber.services.partner.shopify

import reflect.BeanProperty
import java.util.{Collections, Properties, ArrayList}


import com.echoed.chamber.domain.partner.shopify.ShopifyPartner
import collection.JavaConversions._
import org.springframework.beans.factory.FactoryBean
import akka.actor._
import akka.util.Timeout
import akka.util.duration._
import akka.event.Logging
import java.security.MessageDigest
import org.apache.commons.codec.binary.Hex
import dispatch._
import com.echoed.util.ScalaObjectMapper


class ShopifyAccessDispatchActor extends FactoryBean[ActorRef] {


    @BeanProperty var shopifySecret: String = _
    @BeanProperty var shopifyApiKey: String = _

    @BeanProperty var properties: Properties = _


    @BeanProperty var timeoutInSeconds = 20
    @BeanProperty var actorSystem: ActorSystem = _

    @BeanProperty var httpClient: Http = _

    def getObjectType = classOf[ActorRef]

    def isSingleton = true

    def getObject = actorSystem.actorOf(Props(new Actor {

        implicit val timeout = Timeout(timeoutInSeconds seconds)
        private final val logger = Logging(context.system, this)

        override def preStart {
            //NOTE: getting the properties like this is necessary due to a bug in Akka's Spring integration
            //where placeholder values were not being resolved
            {
                shopifySecret = properties.getProperty("shopifySecret")
                shopifyApiKey = properties.getProperty("shopifyApiKey")

                shopifySecret != null && shopifyApiKey != null
            } ensuring(_ == true, "Missing parameters")
        }

        private def fetch[T](path: String, shop: String, password: String, valueType: Class[T])(callback: T => Unit) {
            val resourceUrl = (host(shop).secure / path).subject.build().getUrl
            httpClient(url(resourceUrl) as_! (shopifyApiKey, password) OK As.string).onSuccess {
                case s =>
                    logger.debug("Fetched Shopify resource {}", resourceUrl)
                    callback(ScalaObjectMapper(s, valueType, true))
            }.onFailure {
                case e => logger.error("Error executing {}: {}", resourceUrl, e)
            }
        }

        private def fetchShop(shop: String, password: String)(callback: ShopifyPartner => Unit) {
            fetch("/admin/shop.json", shop, password, classOf[shop]) { s =>
                callback(s.asShopifyPartner(password))
            }
        }

        def receive = {

            case msg @ FetchPassword(shop, signature, t, timeStamp) =>
                val channel = context.sender
                channel ! FetchPasswordResponse(msg, Right(getShopifyPassword(shop, signature, t, timeStamp)))


            case msg @ FetchProduct(shop, password, productId) =>
                val channel = context.sender
                fetch("/admin/products/%s.json" format productId, shop, password, classOf[product]) { p =>
                    channel ! FetchProductResponse(msg, Right(p))
                }


            case msg @ FetchOrder(shop, password, orderId) =>
                val channel = context.sender
                fetch("/admin/orders/%s.json" format orderId, shop, password, classOf[order]) { o =>
                    channel ! FetchOrderResponse(msg, Right(o))
                }


            case msg @ FetchShopFromToken(shop, signature, t, timeStamp) =>
                val channel = context.sender
                fetchShop(shop, getShopifyPassword(shop, signature, t, timeStamp)) { sp =>
                    channel ! FetchShopFromTokenResponse(msg, Right(sp))
                }


            case msg @ FetchShop(shop, password) =>
                val channel = context.sender
                fetchShop(shop, password)(sp => channel ! FetchShopResponse(msg, Right(sp)))
        }


        private def getShopifyPassword(shop: String, signature: String, t: String, timeStamp: String) =
                computeAPIPassword(signature, Map(
                    "shop" -> shop,
                    "t" -> t,
                    "timestamp" -> timeStamp))

	    private def computeAPIPassword(signature: String, params: Map[String, String]) =
                if (isValidShopifyResponse(signature, params)) toMD5Hex(shopifySecret + params("t"))
                else ""

	    private def isValidShopifyResponse(signature: String, params: Map[String, String]) =
	            signature == toMD5Hex(generatePreDigest(params))

    	private def generatePreDigest(params: Map[String, String]) = {
		    val sortedKeys = new ArrayList[String](params.keySet);
		    Collections.sort(sortedKeys)

		    val preDigest = new StringBuilder(shopifySecret)
		    sortedKeys.foreach { sk =>
		        preDigest
		            .append(sk)
		            .append("=")
		            .append(params(sk))
            }
		    preDigest.toString()
	    }

	    private def toMD5Hex(message: String) =
	            Hex.encodeHexString(MessageDigest.getInstance("MD5").digest(message.getBytes))

    }), "ShopifyAccess")
}


case class shop(
        id: Long,
        name: String,
        domain: String,
        myshopify_domain: String,
        shop_owner: String,
        email: String,
        phone: String,
        address1: String,
        city: String,
        zip: String,
        country: String,
        currency: String) {

    def asShopifyPartner(password: String) = new ShopifyPartner(
            shopifyId = id.toString,
            domain = domain,
            name = name,
            zip = zip,
            shopOwner = shop_owner,
            email = email,
            phone = phone,
            country = country,
            city = city,
            shopifyDomain = myshopify_domain,
            password = password)

}


case class order(
        id: Long,
        order_number: Long,
        customer: Customer,
        line_items: Array[LineItem]) {

    val orderNumber = order_number.toString
    val lineItems = line_items
}

case class Customer(id: Long)

case class LineItem(
        id: Long,
        product_id: Long,
        name: String,
        title: String,
        price: String) {

    val productId = product_id.toString
}

case class product(
        id: Long,
        title: String,
        product_type: String,
        body_html: String,
        handle: String,
        images: Array[Image]) {

    val productType = product_type
    val bodyHtml = body_html
}


case class Image(src: String)