import akka.NotUsed
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import demo.api.basket.{Basket, BasketService, ExtraTransportExceptions, Item}
import demo.impl.basket.{BasketApplication, BasketSerializerRegistry}
import org.scalatest.{AsyncWordSpec, BeforeAndAfterAll, Matchers}

import scala.concurrent.Future

class BasketServiceSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll with ExtraTransportExceptions {
  lazy val service = ServiceTest.startServer(ServiceTest.defaultSetup.withCassandra(true)) { ctx =>
    new BasketApplication(ctx) with LocalServiceLocator {
      override def jsonSerializerRegistry: JsonSerializerRegistry = BasketSerializerRegistry
    }
  }

  override protected def beforeAll() = service
  override protected def afterAll() = service.stop()
  val client = service.serviceClient.implement[BasketService]

  "Basket Service" should {
    "add a single item and get the basket" in {
      client.addItem("basket1").invoke(Item("Apple", 50)).flatMap { response =>
        response should ===(NotUsed)

        client.getBasket("basket1").invoke().map { getItemsResponse =>
          getItemsResponse should ===(Basket(Seq(Item("Apple", 50)), 50))
        }
      }
    }

    "get an empty basket" in {
      client.getBasket("basket2").invoke().map { getItemsResponse =>
        getItemsResponse should ===(Basket(Seq(), 0))
      }
    }

    "add multiple items" in {
      val items = "Apple" -> 50 :: "Apple" -> 50 :: "Orange" -> 30 :: Nil

      Future.sequence(items.map(i => client.addItem("basket3").invoke(Item(i._1, i._2)))).flatMap{ f =>
        client.getBasket("basket3").invoke().flatMap { getItemsResponse =>
          getItemsResponse.items should contain(Item("Apple", 50))
          getItemsResponse.items should contain(Item("Orange", 30))
          getItemsResponse.total should===(130)

          client.getTotal("basket3").invoke().map { getItemsResponse =>
            getItemsResponse should===(130)
          }
        }
      }
    }

    "clear the basket" in {
      val items = "Apple" -> 50 :: "Apple" -> 50 :: "Orange" -> 30 :: Nil

      Future.sequence(items.map(i => client.addItem("basket4").invoke(Item(i._1, i._2)))).flatMap{ f =>
        client.clearAll("basket4").invoke().flatMap { clearBasketResponse =>
          clearBasketResponse should ===(NotUsed)

          client.getBasket("basket4").invoke().map { basketResponse =>
            basketResponse should===(Basket(Seq(), 0))
          }
        }
      }
    }

    "place an order" in {
      val client = service.serviceClient.implement[BasketService]
      client.addItem("basket5").invoke(Item("Apple", 50)).flatMap { response =>
        response should ===(NotUsed)

        client.placeOrder("basket5").invoke().map { response =>
          response should ===(NotUsed)
        }
      }
    }

    "return an error if an item is added after the order is placed" in {
      val client = service.serviceClient.implement[BasketService]

      recoverToSucceededIf[BadRequest] {
        for (a <- client.addItem("basket6").invoke(Item("Apple", 50));
             b <- client.placeOrder("basket6").invoke();
             c <- client.addItem("basket6").invoke(Item("Apple", 50)))
          yield c should ===(NotUsed)
      }
    }

    "return an error if clear all is called after the order is placed" in {
      val client = service.serviceClient.implement[BasketService]

      recoverToSucceededIf[BadRequest] {
        for (a <- client.addItem("basket7").invoke(Item("Apple", 50));
             b <- client.placeOrder("basket7").invoke();
             c <- client.clearAll("basket7").invoke())
          yield c should ===(NotUsed)
      }
    }

    "return an error if order is placed on an empty basket" in {
      val client = service.serviceClient.implement[BasketService]

      recoverToSucceededIf[BadRequest] {
        client.placeOrder("basket8").invoke().map { r =>
          r should ===(NotUsed)
        }
      }
    }
  }
}
