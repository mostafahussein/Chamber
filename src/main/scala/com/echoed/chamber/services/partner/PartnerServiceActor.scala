package com.echoed.chamber.services.partner

import org.slf4j.LoggerFactory
import akka.actor.{Channel, Actor}
import com.echoed.util.{ScalaObjectMapper, Encrypter}
import org.codehaus.jackson.`type`.TypeReference
import com.echoed.chamber.domain.views.EchoPossibilityView
import scala.reflect.BeanProperty
import java.util.Date
import akka.dispatch.Future
import scalaz._
import Scalaz._
import org.springframework.transaction.support.TransactionTemplate
import com.echoed.util.TransactionUtils._
import org.springframework.transaction.TransactionStatus
import com.echoed.chamber.dao._
import com.echoed.chamber.domain._
import com.echoed.chamber.services.image.{ProcessImageResponse, ImageService}

class PartnerServiceActor(
        partner: Retailer,
        partnerDao: RetailerDao,
        partnerSettingsDao: RetailerSettingsDao,
        echoDao: EchoDao,
        echoMetricsDao: EchoMetricsDao,
        imageDao: ImageDao,
        imageService: ImageService,
        transactionTemplate: TransactionTemplate,
        encrypter: Encrypter) extends Actor {

    private final val logger = LoggerFactory.getLogger(classOf[PartnerServiceActor])

    self.id = "Partner:%s" format partner.id


    private def requestEcho(
            echoRequest: EchoRequest,
            browserId: String,
            ipAddress: String,
            userAgent: String,
            referrerUrl: String,
            echoClickId: Option[String] = None,
            view: Option[String] = None) = {

        val partnerSettings = Option(partnerSettingsDao.findByActiveOn(partner.id, new Date))
                .getOrElse(throw new PartnerNotActive(partner.id))

        val echoes = echoRequest.items.map { i =>
            Echo.make(
                retailerId = partner.id,
                customerId = echoRequest.customerId,
                productId = i.productId,
                boughtOn = echoRequest.boughtOn,
                step = "request",
                orderId = echoRequest.orderId,
                price = i.price,
                imageUrl = i.imageUrl,
                landingPageUrl = i.landingPageUrl,
                productName = i.productName,
                category = i.category,
                brand = i.brand,
                description = i.description.take(1023),
                echoClickId = echoClickId.orNull,
                browserId = browserId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                referrerUrl = referrerUrl,
                partnerSettingsId = partnerSettings.id,
                view = view.orNull)
        }.map { ec =>
            try {
                //check for existing echo in the case of a page refresh we do not want to bomb out...
                Option(echoDao.findByEchoPossibilityId(ec.echoPossibilityId)).getOrElse {
                    transactionTemplate.execute({status: TransactionStatus =>
                        val echoMetrics = new EchoMetrics(ec, partnerSettings)
                        val img = Option(imageDao.findByUrl(ec.image.url)).getOrElse {
                            logger.debug("New image for processing {}", ec.image.url)
                            imageDao.insert(ec.image)
                            imageService.processImage(ec.image).onComplete(_.value.get.fold(
                                e => logger.error("Unexpected error processing image for echo %s" format ec.id, e),
                                _ match {
                                    case ProcessImageResponse(_, Left(e)) => logger.error("Error processing image for echo %s" format ec.id, e)
                                    case ProcessImageResponse(_, Right(image)) => logger.debug("Successfully processed image for echo {}", ec.id)
                                }
                            ))
                            ec.image
                        }
                        echoMetricsDao.insert(echoMetrics)
                        val echo = ec.copy(echoMetricsId = echoMetrics.id, image = img)
                        echoDao.insert(echo)
                        echo
                    })
                }
            } catch { case e => logger.error("Could not save %s" format ec, e); e }
        }.filter(_.isInstanceOf[Echo]).map(_.asInstanceOf[Echo])

        if (echoes.isEmpty) throw new InvalidEchoRequest()
        else new EchoPossibilityView(echoes, partner, partnerSettings)
    }

    def receive = {

        case msg @ GetPartnerSettings() =>
            val channel: Channel[GetPartnerSettingsResponse] = self.channel
            Option(partnerSettingsDao.findByActiveOn(partner.id, new Date)).cata(
                rs => channel ! GetPartnerSettingsResponse(msg, Right(rs)),
                channel ! GetPartnerSettingsResponse(msg, Left(new PartnerNotActive(partner.id))))


        case msg @ GetPartner() =>
            val channel: Channel[GetPartnerResponse] = self.channel
            channel ! GetPartnerResponse(msg, Right(partner))


        case msg @ RequestEcho(request, browserId, ipAddress, userAgent, referrerUrl, echoedUserId, echoClickId, view) =>
            val channel: Channel[RequestEchoResponse] = self.channel

            logger.debug("Received {}", msg)
            try {
                val decryptedRequest = encrypter.decrypt(request, partner.secret)
                logger.debug("Partner {} received echo request {}", partner.name, decryptedRequest)

                val echoRequest: EchoRequest = new ScalaObjectMapper().readValue(
                        decryptedRequest,
                        new TypeReference[EchoRequest]() {})

                channel ! RequestEchoResponse(msg, Right(requestEcho(
                        echoRequest,
                        browserId,
                        ipAddress,
                        userAgent,
                        referrerUrl,
                        echoClickId,
                        view)))
            } catch {
                case e: InvalidEchoRequest => channel ! RequestEchoResponse(msg, Left(e))
                case e: PartnerNotActive => channel ! RequestEchoResponse(msg, Left(e))
                case e =>
                    logger.error("Error processing %s" format e, msg)
                    channel ! RequestEchoResponse(msg, Left(PartnerException("Error during echo request", e)))
            }

        case msg @ RequestShopifyEcho(order, browserId, ipAddress, userAgent, referrerUrl, echoedUserId, echoClickId) =>
            val channel: Channel[RequestShopifyEchoResponse] = self.channel

            logger.debug("Received {}", msg)
            try {
                val items = order.lineItems.map { li => 
                    val ei = new EchoItem()
                    ei.productId = li.productId
                    ei.productName = li.product.title
                    ei.category = li.product.category
                    ei.brand = partner.name
                    ei.price = li.price.toFloat
                    ei.imageUrl = li.product.imageSrc
                    ei.landingPageUrl = li.product.imageSrc
                    ei.description = li.product.description
                    ei
                }


                val echoRequest: EchoRequest = new EchoRequest()
                echoRequest.items = items
                echoRequest.customerId = order.customerId
                echoRequest.orderId = order.orderId
                echoRequest.boughtOn = new Date
                logger.debug("Echo Request: {}", echoRequest)

                channel ! RequestShopifyEchoResponse(msg, Right(requestEcho(
                        echoRequest,
                        browserId,
                        ipAddress,
                        userAgent,
                        referrerUrl,
                        echoClickId)))
            } catch {
                case e: InvalidEchoRequest => channel ! RequestShopifyEchoResponse(msg, Left(e))
                case e: PartnerNotActive => channel ! RequestShopifyEchoResponse(msg, Left(e))
                case e =>
                    logger.error("Error processing %s" format e, msg)
                    channel ! RequestShopifyEchoResponse(msg, Left(PartnerException("Error during echo request", e)))
            }

        case msg @ RecordEchoStep(echoId, step, echoedUserId, echoClickId) =>
            val channel: Channel[RecordEchoStepResponse] = self.channel

            logger.debug("Processing {}", msg)

            Future {
                echoDao.findById(echoId)
            }.onComplete(_.value.get.fold(
                e => channel ! RecordEchoStepResponse(msg, Left(PartnerException("Error retrieving echo %s" format echoId, e))),
                ep => {
                    val partnerSettings = partnerSettingsDao.findById(ep.retailerSettingsId)
                    val epv = new EchoPossibilityView(ep, partner, partnerSettings)
                    if (ep.isEchoed) {
                        channel ! RecordEchoStepResponse(msg, Left(EchoExists(epv)))
                    } else {
                        channel ! RecordEchoStepResponse(msg, Right(epv))
                        echoDao.updateForStep(ep.copy(step = "%s,%s" format(ep.step, step)))
                        logger.debug("Recorded step {} for echo {}", step, ep.id)
                    }
            })).onException {
                case e =>
                    channel ! RecordEchoStepResponse(msg, Left(PartnerException("Unexpected error", e)))
                    logger.error("Error processing %s" format msg, e)
            }
    }

}

class EchoRequest {
    @BeanProperty var customerId: String = _
    @BeanProperty var orderId: String = _
    @BeanProperty var boughtOn: Date = _
    @BeanProperty var items: List[EchoItem] = _
}

class EchoItem {
    @BeanProperty var productId: String = _
    @BeanProperty var productName: String = _
    @BeanProperty var category: String = _
    @BeanProperty var brand: String = _
    @BeanProperty var price: Float = 0
    @BeanProperty var imageUrl: String = _
    @BeanProperty var landingPageUrl: String = _
    @BeanProperty var description: String = _
}
