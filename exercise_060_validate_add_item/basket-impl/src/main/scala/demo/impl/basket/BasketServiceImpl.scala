package demo.impl.basket

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.InvalidCommandException
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry}
import demo.api.basket.{Basket, BasketService, ExtraTransportExceptions, Item}

import scala.collection.immutable
import scala.concurrent.ExecutionContext

class BasketServiceImpl(persistentEntities: PersistentEntityRegistry)(implicit ec: ExecutionContext)
  extends BasketService with ExtraTransportExceptions{
  override def getBasket(basketId: String): ServiceCall[NotUsed, Basket] = ServiceCall { req =>
    persistentEntities.refFor[BasketEntity](basketId).ask(GetBasket)
  }

  override def addItem(basketId: String): ServiceCall[Item, NotUsed] = ServiceCall { item =>
    persistentEntities.refFor[BasketEntity](basketId).ask(AddItem(item))
      .map(_ => NotUsed)
      .recoverWith {
        case e: InvalidCommandException => throw BadRequest(e.message)
      }
  }

  override def getTotal(basketId: String): ServiceCall[NotUsed, Int] = ServiceCall { req =>
    persistentEntities.refFor[BasketEntity](basketId).ask(GetTotal)
  }

  override def clearAll(basketId: String): ServiceCall[NotUsed, NotUsed] = ServiceCall { req =>
    persistentEntities.refFor[BasketEntity](basketId).ask(ClearAll)
      .map(x => NotUsed)
      .recoverWith {
        case e: InvalidCommandException => throw BadRequest(e.message)
      }
  }

  override def placeOrder(basketId: String): ServiceCall[NotUsed, NotUsed] = ServiceCall { req =>
    persistentEntities.refFor[BasketEntity](basketId).ask(PlaceOrder).map(_ => NotUsed)
  }

  override def placedOrders: Topic[demo.api.basket.OrderPlaced] =
      TopicProducer.taggedStreamWithOffset(BasketEntityEvent.Tag.allTags.to[immutable.Seq]) { (tag, offset) =>
        persistentEntities.eventStream(tag, offset).collect {
          case EventStreamElement(t, OrderPlaced(id, basket), o)  => {
            demo.api.basket.OrderPlaced(id, basket) -> o
          }
        }
    }
}