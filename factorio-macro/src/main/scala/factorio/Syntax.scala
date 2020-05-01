package factorio

trait Syntax {

  type named = annotations.named
  type provides = annotations.provides
  type replicated = annotations.replicated

  object EmptyRecipe extends Recipe

  def assemble[T]: Assembler[T] = new Assembler[T]

  implicit def intellijHack[T, R <: Recipe](in: Assembler[T]): R => () => T = ???
}
