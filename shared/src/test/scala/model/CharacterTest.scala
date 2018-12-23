package model

import model.Alteration._
import org.scalatest.FunSuite

/** *
  * Test for the Character class, and classes composing it
  *
  * @author Nicola Atti
  */
class CharacterTest extends FunSuite {
  import utilities.ScalaProlog._
  private val jacob = getCharacter("Jacob")
  private val annabelle = getCharacter("Annabelle")

  test("New character should have his name and statistics at the creation") {
    assert(
      jacob.characterName.equals("Jacob") &&
        jacob.statistics.strength == 51 &&
        jacob.statistics.agility == 41 &&
        jacob.statistics.spirit == 20 &&
        jacob.statistics.intelligence == 5 &&
        jacob.statistics.resistance == 25)
  }

  test("New character should have the right sub-statistics") {
    assert(
      jacob.physicalDamage == 102 &&
        jacob.physicalCriticalDamage == 170 &&
        jacob.speed == 5 &&
        jacob.criticalChance == 20 &&
        jacob.magicalDefence == 20 &&
        jacob.status.maxManaPoints == 100 &&
        jacob.magicalPower == 2 &&
        jacob.magicalCriticalPower == 150 &&
        jacob.status.maxHealthPoints == 750 &&
        jacob.physicalDefence == 37)
  }

  test("New character should have his special moves") {
    val jacobSpecialMovesName = List("Skullcrack", "Sismic Slam", "Berserker Rage", "Second Wind")
    assert(
      jacob.specialMoves
        .map { case (specialMoveName, _) => jacobSpecialMovesName.contains(specialMoveName) }
        .forall(_ == true))
  }

  test("New character should not have modifiers or alterations") {
    assert(
      jacob.status.modifiers.isEmpty &&
        jacob.status.alterations.isEmpty)
  }

  test("The initial health and mp should be equal to its maximum value when created") {
    assert(
      jacob.status.healthPoints == jacob.status.maxHealthPoints && //540
        jacob.status.manaPoints == jacob.status.maxManaPoints) //65
  }

  test("Different character should have a different name") {
    assert(!(jacob.characterName equals annabelle.characterName))
  }

  test("Other classes should have the right statistics") {
    assert(
      annabelle.physicalDamage == 58 &&
        annabelle.physicalCriticalDamage == 161 &&
        annabelle.speed == 8 &&
        annabelle.criticalChance == 40 &&
        annabelle.magicalDefence == 22 &&
        annabelle.status.maxManaPoints == 110 &&
        annabelle.magicalPower == 4 &&
        annabelle.magicalCriticalPower == 150 &&
        annabelle.status.maxHealthPoints == 675 &&
        annabelle.physicalDefence == 27)
  }
  test("A character whose hit points are more than zero should be considered alive"){
    assert(jacob.isAlive)
    val status = Status(0,50,200,100,Map(),Map())
    jacob.status = status
    assert(!jacob.isAlive)
  }
  test("A stunned or asleep character should be incapacitated"){
    val asleepStatus = Status(100,100,100,100,Map(),Map((Asleep,3)))
    val stunnedStatus = Status(100,100,100,100,Map(),Map((Stunned,1)))
    jacob.status = asleepStatus
    annabelle.status = stunnedStatus
    assert(jacob.isIncapacitated && annabelle.isIncapacitated)
  }
}
