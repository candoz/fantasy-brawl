package controller

import java.net.URL
import java.util.ResourceBundle

import communication.StatusUpdateMessage._
import game.{Battle, Round}
import javafx.animation.{Animation, KeyFrame, Timeline}
import javafx.collections.{FXCollections, ObservableList}
import javafx.event.ActionEvent
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.{Button, Label, ListView}
import javafx.scene.effect.DropShadow
import javafx.scene.image.{Image, ImageView}
import javafx.scene.input.MouseEvent
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.util.Duration
import messaging.BattleManager
import model.{Character, Move, PhysicalAttack}
import view.ApplicationView
import view.ViewConfiguration.ViewSelector._
import ActSelector._

import scala.collection.mutable.ListBuffer

/** Controller for the Battle GUI.
  *
  * @author Nicola Atti
  */
object BattleController extends Initializable with ViewController {

  import BattleControllerHelper._

  val controller: ViewController = this

  @FXML var playerCharNames: VBox = _
  @FXML var playerHps: VBox = _
  @FXML var playerMps: VBox = _
  @FXML var playerAlterations: VBox = _

  @FXML var opponentCharNames: VBox = _
  @FXML var opponentHps: VBox = _
  @FXML var opponentMps: VBox = _
  @FXML var opponentAlterations: VBox = _

  @FXML var playerChar1Image: ImageView = _
  @FXML var playerChar2Image: ImageView = _
  @FXML var playerChar3Image: ImageView = _
  @FXML var playerChar4Image: ImageView = _

  @FXML var opponentChar1Image: ImageView = _
  @FXML var opponentChar2Image: ImageView = _
  @FXML var opponentChar3Image: ImageView = _
  @FXML var opponentChar4Image: ImageView = _

  @FXML var timerCounter: Label = _
  @FXML var roundCounter: Label = _

  @FXML var moveListView: ListView[String] = _

  @FXML var actButton: Button = _
  @FXML var passButton: Button = _

  @FXML var winnerLabel: Label = _
  @FXML var toMenuButton: Button = _

  @FXML var playerIdLabel: Label = _
  @FXML var opponentIdLabel: Label = _

  @FXML var moveReportListView: ListView[String] = _

  var playerImages: List[ImageView] = List()
  var opponentImages: List[ImageView] = List()

  var playerCharacterImages: Map[ImageView, Character] = Map()
  var opponentCharacterImages: Map[ImageView, Character] = Map()

  var targetImages: ListBuffer[ImageView] = ListBuffer()
  var targets: ListBuffer[Character] = ListBuffer()

  var moveList: ObservableList[String] = FXCollections.observableArrayList()
  var activeCharacter: Character = _
  var activeLabel: Label = _
  var activeImage: ImageView = _

  val timeline: Timeline = new Timeline()
  var timeSeconds: Int = config.MiscSettings.TurnDurationInSeconds
  timeline.setCycleCount(Animation.INDEFINITE)
  timeline.getKeyFrames.add(
    new KeyFrame(
      Duration.seconds(1),
      (_: ActionEvent) => {
        timeSeconds = timeSeconds - 1
        timerCounter.setText(timeSeconds.toString)
        if (timeSeconds <= 0) {
          timeline.stop()
          if (activeCharacter.owner.get == Battle.playerId) {
            skipTurnAndDisplay()
          }
        }
      }
    ))

  override def initialize(location: URL, resources: ResourceBundle): Unit = {
    TargetedEffect.setHeight(CharacterSelectionDimension)
    TargetedEffect.setWidth(CharacterSelectionDimension)
    TargetedEffect.setSpread(CharacterSelectionSpread)
    ActivePlayerEffect.setHeight(CharacterSelectionDimension)
    ActivePlayerEffect.setWidth(CharacterSelectionDimension)
    ActivePlayerEffect.setSpread(CharacterSelectionSpread)
    ActiveOpponentEffect.setHeight(CharacterSelectionDimension)
    ActiveOpponentEffect.setWidth(CharacterSelectionDimension)
    ActiveOpponentEffect.setSpread(CharacterSelectionSpread)

    playerImages = List(playerChar1Image, playerChar2Image, playerChar3Image, playerChar4Image)
    opponentImages = List(opponentChar1Image, opponentChar2Image, opponentChar3Image, opponentChar4Image)

    setBattlefield()
    timeline.playFromStart()

    playerCharNames.getChildren.forEach(label => label.asInstanceOf[Label].setTextFill(Color.DARKSLATEGRAY))
    opponentCharNames.getChildren.forEach(label => label.asInstanceOf[Label].setTextFill(Color.DARKSLATEGRAY))
  }

  /** Setups the GUI for both teams */
  def setBattlefield(): Unit = {
    playerIdLabel.setText(Battle.playerId)
    opponentIdLabel.setText(Battle.opponentId)
    setupTeams(Battle.teams)
  }

  /** Shows the match winner and activates the button to return to the TeamSelection
    * menu.
    *
    * @param winner the winner's name of the match
    */
  def settingWinner(winner: String): Unit = {
    timeline.stop()
    winnerLabel.setText("WINNER IS " + winner.toUpperCase)
    winnerLabel.setVisible(true)
    actButton.setDisable(true)
    passButton.setDisable(true)
    toMenuButton.setDisable(false)
    toMenuButton.setVisible(true)
  }

  /** Sets the player's team images and status description labels.
    *
    * @param team the team to setup
    */
  def setupTeams(team: Set[Character]): Unit = {
    playerCharacterImages = (playerImages zip team.filter(character => character.owner.get == Battle.playerId)).toMap
    opponentCharacterImages =
      (opponentImages zip team.filter(character => character.owner.get == Battle.opponentId)).toMap
    prepareImages()
    setupLabels()
  }

  /** Updates the status of each character in the battle,
    * showing it's current health and mana values compared with it' maximum
    * and by showing the various alterations that are active.
    *
    * After that changes the images and labels of dead characters.
    */
  def updateStatus(): Unit = {
    (playerCharacterImages.values zip playerHps.getChildren.toArray) foreach (couple =>
      couple._2
        .asInstanceOf[Label]
        .setText(couple._1.status.healthPoints + Separator + couple._1.status.maxHealthPoints))
    (playerCharacterImages.values zip playerMps.getChildren.toArray) foreach (couple =>
      couple._2.asInstanceOf[Label].setText(couple._1.status.manaPoints + Separator + couple._1.status.maxManaPoints))
    (playerCharacterImages.values zip playerAlterations.getChildren.toArray) foreach (couple =>
      couple._2
        .asInstanceOf[Label]
        .setText(
          couple._1.status.alterations.keySet.map(alt => alt.acronym).foldRight("")(_ + Separator + _).dropRight(1)))

    (opponentCharacterImages.values zip opponentHps.getChildren.toArray) foreach (couple =>
      couple._2
        .asInstanceOf[Label]
        .setText(couple._1.status.healthPoints + Separator + couple._1.status.maxHealthPoints))
    (opponentCharacterImages.values zip opponentMps.getChildren.toArray) foreach (couple =>
      couple._2.asInstanceOf[Label].setText(couple._1.status.manaPoints + Separator + couple._1.status.maxManaPoints))
    (opponentCharacterImages.values zip opponentAlterations.getChildren.toArray) foreach (couple =>
      couple._2
        .asInstanceOf[Label]
        .setText(
          couple._1.status.alterations.keySet.map(alt => alt.acronym).foldRight("")(_ + Separator + _).dropRight(1)))

    setDeadCharacters()
  }

  /** Given the active character from the Battle changes the corresponding label.
    *
    * The label is painted green to symbolize which character has now the turn, if
    * the player owns the character, the GUI will show his move list.
    */
  def setActiveCharacter(character: Character): Unit = {
    if (activeLabel != null) {
      activeLabel.setTextFill(Color.DARKSLATEGRAY)
      activeImage.setEffect(null)
    }
    activeCharacter = character
    if (activeCharacter.owner.get == Battle.playerId) {
      activeLabel = playerCharNames.getChildren.toArray
        .filter(charName => charName.asInstanceOf[Label].getText equals activeCharacter.characterName)
        .head
        .asInstanceOf[Label]
      activeLabel.setTextFill(CharacterPlayerSelectionLabelColor)
      activeImage = playerCharacterImages.find(char => char._2 == activeCharacter).get._1
      activeImage.setEffect(ActivePlayerEffect)
      passButton.setDisable(false)
      setupCharacterMoves(activeCharacter)
    } else {
      activeLabel = opponentCharNames.getChildren.toArray
        .filter(charName => charName.asInstanceOf[Label].getText equals activeCharacter.characterName)
        .head
        .asInstanceOf[Label]
      activeLabel.setTextFill(CharacterOpponentSelectionLabelColor)
      activeImage = opponentCharacterImages.find(char => char._2 == activeCharacter).get._1
      activeImage.setEffect(ActiveOpponentEffect)
      passButton.setDisable(true)
    }
    resetTimer()
  }

  /** Resets the turn timer. */
  def resetTimer(): Unit = {
    timeSeconds = config.MiscSettings.TurnDurationInSeconds
    timerCounter.setText(timeSeconds.toString)
    timeline.playFromStart()
  }

  /** Empties the target list. */
  def resetTargets(): Unit = {
    targets = ListBuffer()
    targetImages.foreach(target => setCharacterUnselected(target))
    targetImages = ListBuffer()
  }

  /** Displays the move effect showing the player, the move's name and its targets.
    *
    * @param characterUser the character using the move
    * @param moveName the move name
    * @param moveTargets the targets
    */
  def displayMoveEffect(characterUser: Character,
                        moveName: String,
                        moveTargets: Set[Character],
                        actSelector: ActSelector): Unit = {
    var moveReport: String = ""
    actSelector match {
      case SURRENDER =>
        moveReport = "The ENEMY has left the battle."
      case UPDATE =>
        initMoveReport()
        moveReport = moveReport concat "used " concat moveName concat " on \n"
        val playerTargets = moveTargets.filter(character => character.owner.get equals Battle.playerId)
        val opponentTargets = moveTargets.filter(character => character.owner.get equals Battle.opponentId)

        if (playerTargets.nonEmpty) {
          moveReport = moveReport concat " YOUR:"
          playerTargets.foreach(playerChar => moveReport = moveReport concat " " + playerChar.characterName)
        }
        if (opponentTargets.nonEmpty) {
          moveReport = moveReport concat " ENEMY:"
          opponentTargets.foreach(opponentChar => moveReport = moveReport concat " " + opponentChar.characterName)
        }
      case SKIP =>
        initMoveReport()
        moveReport = moveReport concat "skipped the turn"
    }
    moveReportListView.getItems.add(0, moveReport)
    updateStatus()

    def initMoveReport(): Unit = {
      if (characterUser.owner.get equals Battle.playerId) {
        moveReport = s"YOUR ${characterUser.characterName} "
      } else {
        moveReport = s"ENEMY ${characterUser.characterName} "
      }
    }
  }

  /** Provides private operations for the BattleController. */
  private object BattleControllerHelper {
    val ImageExtension: String = ".png"
    val Separator: String = "/"
    val PhysicalAttackRepresentation: String = "Physical Attack"
    val MovesSeparator: String = "--"
    val CharacterSelectionDimension = 20
    val CharacterSelectionSpread = 0.7
    final val TargetedEffect: DropShadow = new DropShadow(0, 0, 0, Color.BLUE)
    final val ActivePlayerEffect: DropShadow = new DropShadow(0, 0, 0, Color.LIME)
    final val ActiveOpponentEffect: DropShadow = new DropShadow(0, 0, 0, Color.RED)
    val CharacterPlayerSelectionLabelColor: Color = Color.LIMEGREEN
    val CharacterOpponentSelectionLabelColor: Color = Color.ORANGERED

    /** Assigns to each character it's battle image, orientation depends on the player. */
    def prepareImages(): Unit = {
      playerCharacterImages.foreach(charImage =>
        charImage._1.setImage(new Image("view/" + charImage._2.characterName + "1" + ImageExtension)))
      opponentCharacterImages.foreach(charImage =>
        charImage._1.setImage(new Image("view/" + charImage._2.characterName + "2" + ImageExtension)))
    }

    /** Writes all the team's labels to display the name of each character in the battle. */
    def setupLabels(): Unit = {
      (playerCharacterImages.values zip playerCharNames.getChildren.toArray) foreach (couple =>
        couple._2.asInstanceOf[Label].setText(couple._1.characterName))
      (opponentCharacterImages.values zip opponentCharNames.getChildren.toArray) foreach (couple =>
        couple._2.asInstanceOf[Label].setText(couple._1.characterName))
      updateStatus()
    }

    /** Checks if the team members of any player are dead and sets their image to
      * a tombstone.
      */
    def setDeadCharacters(): Unit = {
      playerCharacterImages.foreach(couple =>
        if (!couple._2.isAlive) {
          couple._1.setImage(new Image("view/tombstone1.png"))
          couple._1.setDisable(true)
          setDeadLabel(couple._2, couple._2.owner.get)
      })
      opponentCharacterImages.foreach(couple =>
        if (!couple._2.isAlive) {
          couple._1.setImage(new Image("view/tombstone2.png"))
          couple._1.setDisable(true)
          setDeadLabel(couple._2, couple._2.owner.get)
      })
    }

    /** Changes the name and alteration labels of dead characters to reflect it.
      *
      * @param deadCharacter the dead character
      * @param owner the dead character owner
      */
    def setDeadLabel(deadCharacter: Character, owner: String): Unit = {
      if (owner equals Battle.playerId) {
        (playerCharacterImages.values zip playerCharNames.getChildren.toArray)
          .find(couple => couple._1.characterName == deadCharacter.characterName)
          .get
          ._2
          .asInstanceOf[Label]
          .setText("K. O.")
        (playerCharacterImages.values zip playerAlterations.getChildren.toArray)
          .find(couple => couple._1.characterName == deadCharacter.characterName)
          .get
          ._2
          .asInstanceOf[Label]
          .setText("")
      } else {
        (opponentCharacterImages.values zip opponentCharNames.getChildren.toArray)
          .find(couple => couple._1.characterName == deadCharacter.characterName)
          .get
          ._2
          .asInstanceOf[Label]
          .setText("K. O.")
        (opponentCharacterImages.values zip opponentAlterations.getChildren.toArray)
          .find(couple => couple._1.characterName == deadCharacter.characterName)
          .get
          ._2
          .asInstanceOf[Label]
          .setText("")
      }
    }

    /** Shows the player's active character's moves into the listView.
      *
      * @param character the player's active character
      */
    def setupCharacterMoves(character: Character): Unit = {
      moveList.clear()
      moveList.add(PhysicalAttackRepresentation)
      character.specialMoves.foreach(move =>
        moveList.add(move._1 + MovesSeparator + "MP: " + move._2.manaCost + " Max targets: " + move._2.maxTargets))
      moveListView.setItems(moveList)
      moveListView.getSelectionModel.selectFirst()
    }

    /** Empties the move list view. */
    def resetCharacterMoves(): Unit = {
      moveList.clear()
      moveListView.setItems(moveList)
    }

    /** Adds/removes the pressed character to/from the list of targets and shows it.
      *
      * @param imagePressed the character image pressed
      * @param character the character associated with the image
      */
    def setTargets(imagePressed: ImageView, character: Character): Unit = {
      if (targetImages.exists(image => image.getId equals imagePressed.getId)) {
        targetImages -= imagePressed
        targets -= character
        setCharacterUnselected(imagePressed)
      } else {
        targetImages += imagePressed
        targets += character
        setCharacterSelected(imagePressed)
      }
    }

    /** Activates/Deactivates the act button. */
    def actButtonActivation(): Unit = {
      if (canAct) {
        actButton.setDisable(false)
      } else {
        actButton.setDisable(true)
      }
    }

    /** Checks if the act button should be active:
      *
      * The act button is active if the player has targeted at least one character, and the
      * number of targets does not exceed the maximum number specified by the selected move.
      * Furthermore the active character needs to have enough mana points to use the said move.
      *
      * @return if the act button should be active or not
      */
    def canAct: Boolean = {
      if (moveListView.getSelectionModel.getSelectedItems.isEmpty || targetImages.isEmpty) {
        false
      } else {
        val moveName = moveListView.getSelectionModel.getSelectedItem.split(MovesSeparator).head
        var moveMaxTargets = 1
        moveName match {
          case PhysicalAttackRepresentation =>
            targetImages.size <= moveMaxTargets && Move.canMakeMove(activeCharacter, PhysicalAttack)
          case _ =>
            val selectedMove = activeCharacter.specialMoves(moveName)
            moveMaxTargets = selectedMove.maxTargets
            targetImages.size <= moveMaxTargets && Move.canMakeMove(activeCharacter, selectedMove)
        }
      }
    }

    /** Applies the selection effect to the targeted character's image.
      *
      * @param charImage the character's image
      */
    def setCharacterSelected(charImage: ImageView): Unit = {
      charImage.setEffect(TargetedEffect)
    }

    /** Removes the selection effect to the deselected character's image.
      *
      * @param charImage the character's image
      */
    def setCharacterUnselected(charImage: ImageView): Unit = {
      if (playerCharacterImages.contains(charImage) && playerCharacterImages(charImage) == activeCharacter) {
        charImage.setEffect(ActivePlayerEffect)
      } else {
        charImage.setEffect(null)
      }
    }

    /** Initiates the skip turn procedure and displays it. */
    def skipTurnAndDisplay(): Unit = {
      BattleManager.skipTurn((activeCharacter.owner.get, activeCharacter.characterName), Round.roundId)
      displayMoveEffect(activeCharacter, "", Set(), ActSelector.SKIP)
      resetCharacterMoves()
      Round.endTurn()
    }
  }

  /** Handles the press of the Act button. */
  @FXML def handleActButtonPress(): Unit = {
    Round.actCalculation(activeCharacter,
                         moveListView.getSelectionModel.getSelectedItem.split(MovesSeparator).head,
                         targets.toList)
    activeLabel.setTextFill(Color.BLACK)
    resetTargets()
    resetCharacterMoves()
    actButton.setDisable(true)
    passButton.setDisable(true)
  }

  /** Handles the press of the Pass button. */
  @FXML def handlePassButtonPress(): Unit = {
    passButton.setDisable(true)
    skipTurnAndDisplay()
  }

  /** Handles the press of the Moves manual button, showing the moves manual GUI. */
  @FXML def movesManualPressed() {
    ApplicationView.createMovesManualView()
  }

  /** Checks if the act button is to be activated after the selection of a different
    * move.
    * */
  @FXML def handleMoveSelection() {
    actButtonActivation()
  }

  /** Adds/Removes the pressed character to/from the target's list, and checks if
    * the act button is to be activated.
    *
    * @param mouseEvent the click of an character's image
    */
  @FXML def handleCharacterToTargetPressed(mouseEvent: MouseEvent) {
    if (activeCharacter.owner.get == Battle.playerId) {
      val characterPressed: ImageView = mouseEvent.getSource.asInstanceOf[ImageView]
      if (characterPressed.getId.contains("player")) {
        setTargets(characterPressed, playerCharacterImages(characterPressed))
      } else {
        setTargets(characterPressed, opponentCharacterImages(characterPressed))
      }
      actButtonActivation()
    }
  }

  /** Handles the pression of the victory button, returning the player to the TeamSelection
    * menu.
    */
  @FXML def handleToMenuButtonPressed(): Unit = {
    ApplicationView changeView TEAM
  }
}
