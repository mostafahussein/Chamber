package com.echoed.chamber.services.shopify

import akka.dispatch.Future

trait ShopifyUserService {
    
    def getOrder(orderId: Int): Future[GetOrderResponse]
    
    def getShopifyUser: Future[GetShopifyUserResponse]

}
