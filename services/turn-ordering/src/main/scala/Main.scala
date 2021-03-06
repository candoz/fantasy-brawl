import scala.concurrent._
import ExecutionContext.Implicits.global
import akka.actor.{ActorSystem, Props}
import com.spingo.op_rabbit._
import com.spingo.op_rabbit.properties.ReplyTo
import communication.MessageFormat.MyFormat
import communication.StatusUpdateMessage.CharacterKey
import communication.turnordering.PlayerInfo
import config.MessagingSettings
import turnordering.{AsyncDbManager, MongoDbManager}

/** Entry point for the service that provides turn-ordering functionality at the
  * beginning of every round.
  *
  * At every new round, before starting, each player sends the information about
  * his/hers team members' speeds and awaits for a response from the service
  * containing all the ordered turns.
  *
  * @author Daniele Schiavi
  */
object Main extends App {

  val LogMessage = "Received a new turn ordering request"
  val OldRequestPrint = "Received an old round request: current "

  val dbManager: AsyncDbManager = MongoDbManager

  import TurnOrderingHelper._
  import communication._
  val rabbitControl = ActorSystem().actorOf(Props[RabbitControl])

  implicit val recoveryStrategy: RecoveryStrategy = RecoveryStrategy.nack(requeue = MessagingSettings.Requeue)
  implicit val requestFormat: MyFormat[StartRoundRequest] = MessageFormat.format[StartRoundRequest]
  implicit val responseFormat: MyFormat[StartRoundResponse] = MessageFormat.format[StartRoundResponse]

  import Queues._

  val requestQueue =
    Queue(StartRoundRequestQueue, durable = MessagingSettings.Durable, autoDelete = MessagingSettings.AutoDelete)

  Subscription.run(rabbitControl) {
    import com.spingo.op_rabbit.Directives._
    channel(qos = MessagingSettings.Qos) {
      consume(requestQueue) {
        (body(as[StartRoundRequest]) & optionalProperty(ReplyTo)) { (request, replyTo) =>
          {
            import config._
            if (MiscSettings.ServicesLog) {
              println(LogMessage)
            }
            val requestInfo =
              PlayerInfo(request.playerName, request.playerTeamSpeeds, request.battleId, request.round, replyTo.get)

            for {
              round <- dbManager.getCurrentRound(request.battleId) if request.round == round + 1 // else not interested
              _ <- dbManager.addPlayerInfo(requestInfo)
              opponentOpt <- dbManager.getPlayerInfo(request.opponentName, request.battleId, request.round)
              if opponentOpt.isDefined
            } yield {
              opponentOpt match {
                case Some(opponentInfo) => cleanRequestsAndSendOrderedTurns(requestInfo, opponentInfo)
                case _                  => Unit
              }
            }
            ack
          }
        }
      }
    }
  }

  private object TurnOrderingHelper {

    /** Removes both the saved client requests and informs them with the list of
      * ordered turns concerning the appropriate turn.
      *
      * @param requestInfo the info about the player who made the second request
      * @param opponentInfo the info about the player who made the first request
      */
    def cleanRequestsAndSendOrderedTurns(requestInfo: PlayerInfo, opponentInfo: PlayerInfo): Unit = {
      val firstToDelete = if (requestInfo.name > opponentInfo.name) requestInfo.name else opponentInfo.name
      val secondToDelete = if (requestInfo.name > opponentInfo.name) opponentInfo.name else requestInfo.name
      for {
        deleted1 <- dbManager.deletePlayerInfo(firstToDelete, requestInfo.battleId, requestInfo.round) if deleted1
        deleted2 <- dbManager.deletePlayerInfo(secondToDelete, requestInfo.battleId, requestInfo.round) if deleted2
        incremented <- dbManager.incrementCurrentRound(requestInfo.battleId) if incremented
      } yield {
        val allSpeedsOrdered = (extractTeamSpeeds(opponentInfo) ++ extractTeamSpeeds(requestInfo))
          .sortBy { case (_, speed) => speed }
          .reverse
          .map { case (key, _) => key }
        send(allSpeedsOrdered, requestInfo, opponentInfo)
      }
    }

    /** Sends a message to both the battling players.
      *
      * @param speeds the list of tuples (characterOwner, characterName) in decreasing
      *               order according to their speeds
      * @param requestInfo the info about the player who made the second request
      * @param opponentInfo the info about the player who made the first request
      */
    def send(speeds: List[CharacterKey], requestInfo: PlayerInfo, opponentInfo: PlayerInfo): Unit = {
      val response = StartRoundResponse(Right(speeds), requestInfo.round)

      rabbitControl ! Message.queue(response, requestInfo.replyTo)
      rabbitControl ! Message.queue(response, opponentInfo.replyTo)
    }

    /** Extracts a list of tuples (character, speed) from the player team.
      *
      * @param player structure that contains the player team
      * @return list of tuples (character, speed)
      */
    def extractTeamSpeeds(player: PlayerInfo): List[(CharacterKey, Int)] = {
      player.teamSpeeds.map { case (characterName, speed) => (player.name, characterName) -> speed }.toList
    }
  }
}
