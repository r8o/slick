package org.scalaquery.ql

import org.scalaquery.ast.{AnonSymbol, Filter, Node}


/**
 * Marker trait for foreign key and primary key constraints.
 */
trait Constraint

final class ForeignKey[TT <: AbstractTable[_], P]( //TODO Simplify this mess!
    val name: String,
    val sourceTable: Node,
    val onUpdate: ForeignKeyAction,
    val onDelete: ForeignKeyAction,
    val sourceColumns: P,
    val targetColumns: TT => P,
    val linearizedSourceColumns: IndexedSeq[Node],
    val linearizedTargetColumns: IndexedSeq[Node],
    val linearizedTargetColumnsForOriginalTargetTable: IndexedSeq[Node],
    val targetTable: TT)

object ForeignKey {
  def apply[TT <: AbstractTable[_], P](
      name: String,
      sourceTable: Node,
      targetTableUnpackable: Unpackable[TT, _],
      originalTargetTable: TT,
      unpackp: Packing[P, _, _],
      originalSourceColumns: P,
      originalTargetColumns: TT => P,
      onUpdate: ForeignKeyAction,
      onDelete: ForeignKeyAction
    ): ForeignKey[TT, P] = new ForeignKey[TT, P](
      name,
      sourceTable,
      onUpdate,
      onDelete,
      originalSourceColumns,
      originalTargetColumns,
      unpackp.linearizer(originalSourceColumns).getLinearizedNodes,
      unpackp.linearizer(originalTargetColumns(targetTableUnpackable.value)).getLinearizedNodes,
      unpackp.linearizer(originalTargetColumns(originalTargetTable)).getLinearizedNodes,
      targetTableUnpackable.value
    )
}

sealed abstract class ForeignKeyAction(val action: String)

object ForeignKeyAction {
  case object Cascade extends ForeignKeyAction("CASCADE")
  case object Restrict extends ForeignKeyAction("RESTRICT")
  case object NoAction extends ForeignKeyAction("NO ACTION")
  case object SetNull extends ForeignKeyAction("SET NULL")
  case object SetDefault extends ForeignKeyAction("SET DEFAULT")
}

class ForeignKeyQuery[E <: AbstractTable[_], U](
    nodeDelegate: Node,
    base: Unpackable[_ <: E, _ <: U],
    val fks: IndexedSeq[ForeignKey[E, _]],
    targetBaseQuery: Query[E, U],
    generator: AnonSymbol,
    aliasedValue: E
  ) extends WrappingQuery[E, U](nodeDelegate, base) with Constraint {

  /**
   * Combine the constraints of this ForeignKeyQuery with another one with the
   * same target table, leading to a single instance of the target table which
   * satisfies the constraints of both.
   */
  def & (other: ForeignKeyQuery[E, U]): ForeignKeyQuery[E, U] = {
    val newFKs = fks ++ other.fks
    val conditions =
      newFKs.map(fk => ColumnOps.Is(Node(fk.targetColumns(aliasedValue)), Node(fk.sourceColumns))).
        reduceLeft(ColumnOps.And.apply _)
    val newDelegate = Filter(generator, Node(targetBaseQuery), conditions)
    new ForeignKeyQuery[E, U](newDelegate, base, newFKs, targetBaseQuery, generator, aliasedValue)
  }
}

case class PrimaryKey(name: String, columns: IndexedSeq[Node]) extends Constraint
