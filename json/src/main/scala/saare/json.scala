package saare

package object json {
  import scala.language.dynamics
  import scala.util._
  import scala.reflect._
  import scala.language.experimental.macros
  import scala.reflect.macros.Context
  import scala.util.control._
  import scala.util.control.Exception._

  sealed trait JValue
  case class JNumber(value: BigDecimal) extends JValue
  object JNumber {
    def apply(value: Int): JNumber = JNumber(BigDecimal(value))
    def apply(value: Long): JNumber = JNumber(BigDecimal(value))
    def apply(value: Double): JNumber = JNumber(BigDecimal(value))
    def apply(value: BigInt): JNumber = JNumber(BigDecimal(value))
  }
  case class JBoolean(value: Boolean) extends JValue
  case class JString(value: String) extends JValue
  case class JArray(value: Seq[JValue]) extends JValue
  case class JObject(value: Map[String, JValue]) extends JValue
  case object JNull extends JValue
  private[json] object JNothing extends JValue

  def as[A <: JValue: ClassTag] = {
    def f(x: JValue): Option[A] = x match {
      case x: JValue if implicitly[ClassTag[A]].runtimeClass isAssignableFrom x.getClass => Some(x.asInstanceOf[A])
      case _ => None
    }
    f _
  }

  val parse: String => Try[JValue] = x => Jackson.readJValue(x)

  val print: JValue => String = x => Jackson.writeJValue(x)

  val pretty_print: JValue => String = x => Jackson.prettyPrintJValue(x)

  def lens = new Lens(get = (x: JValue) => Some(x), set = (x: JValue) => (y: JValue) => Some(y))

  class Lens(private val get: JValue => Option[JValue], private val set: JValue => JValue => Option[JValue]) extends Dynamic {
    def selectDynamic(name: String) = {
      val get = this.get.andThen {
        case Some(JObject(xs)) => xs.get(name)
        case Some(JArray(xs)) => name.parseInt().flatMap(xs lift _)
        case _ => None
      }
      val set = (x: JValue) => (y: JValue) => {
        this.get(x) match {
          case Some(JObject(xs)) => this.set(x)(JObject(xs.updated(name, y)))
          case Some(JArray(xs)) => {
            for {
              index <- name.parseInt()
              if xs.isDefinedAt(index)
              ret <- this.set(x)(JArray(xs.updated(index, y)))
            } yield ret
          }
          case _ => None
        }
      }
      new Lens(get, set)
    }
  }

  object Lens {
    val get: Lens => JValue => Option[JValue] = x => x.get
    val set: Lens => JValue => JValue => Option[JValue] = x => x.set
  }

  trait Encoder[A] {
    def encode: A => JValue
  }
  trait Decoder[A] {
    def decode: JValue => Option[A]
  }
  case class Codec[A](encode: A => JValue, decode: JValue => Option[A]) extends Encoder[A] with Decoder[A]

  implicit def JValueCodec[A <: JValue: ClassTag] = Codec[A](encode = x => x, decode = as[A])

  def encode[A: Encoder](x: A) = implicitly[Encoder[A]].encode(x)

  def decode[A: Decoder](x: JValue) = implicitly[Decoder[A]].decode(x)

  implicit def IntCodec = Codec[Int](encode = x => JNumber(x), decode = as[JNumber].andThen(_.flatMap { case JNumber(x) => x.toIntOption }))
  implicit def LongCodec = Codec[Long](encode = x => JNumber(x), decode = as[JNumber].andThen(_.flatMap { case JNumber(x) => x.toLongOption }))
  implicit def DoubleCodec = Codec[Double](encode = x => JNumber(x), decode = as[JNumber].andThen(_.flatMap { case JNumber(x) => x.toDoubleOption }))
  implicit def BigIntCodec = Codec[BigInt](encode = x => JNumber(x), decode = as[JNumber].andThen(_.flatMap { case JNumber(x) => x.toBigIntOption }))
  implicit def BigDecimalCodec = Codec[BigDecimal](encode = x => JNumber(x), decode = as[JNumber].andThen(_.map { case JNumber(x) => x }))
  implicit def BooleanCodec = Codec[Boolean](encode = x => JBoolean(x), decode = as[JBoolean].andThen(_.map(_.value)))
  implicit def StringCodec = Codec[String](encode = x => JString(x), decode = as[JString].andThen(_.map(_.value)))
  implicit def OptionCodec[A: Codec] = Codec[Option[A]](encode = x => x.map(encode(_)).getOrElse(JNothing), decode = { case JNothing | JNull => Some(None); case x => decode[A](x).map(Some(_)) })
  implicit def SeqCodec[A: Codec] = {
    val decode: JValue => Option[Seq[A]] = {
      case JArray(xs) =>
        xs.foldLeft[Option[Seq[A]]](Some(Seq())) {
          case (Some(xs), value) => for (x <- json.decode[A](value)) yield xs :+ x
          case (None, _) => None
        }
      case _ => None
    }
    Codec[Seq[A]](encode = x => JArray(x.map(encode(_))), decode = decode)
  }
  implicit def MapCodec[A: Codec] = {
    val decode: JValue => Option[Map[String, A]] = {
      case JObject(xs) =>
        xs.foldLeft[Option[Map[String, A]]](Some(Map())) {
          case (Some(xs), value) => for (x <- json.decode[A](value._2)) yield xs + (value._1 -> x)
          case (None, _) => None
        }
      case _ => None
    }
    Codec[Map[String, A]](encode = x => JObject(x.mapValues(encode(_))), decode = decode)
  }
  def CaseClassCodecImpl[A: c.WeakTypeTag](c: Context): c.Expr[json.Codec[A]] = {
    import c.universe._
    val `type` = weakTypeOf[A]
    val typeName = `type`.typeSymbol
    val companion = {
      def f: c.Tree = {
        val symbol = `type`.typeSymbol.companionSymbol.orElse {
          // due to SI-7567, if A is a inner class, companionSymbol returns NoSymbol...
          // as I don't know how to avoid SI-7567, let's fall back into a anaphoric macro!
          return c.parse(typeName.name.decoded)
        }
        q"$symbol"
      }
      f
    }
    val params = `type`.declarations.collectFirst { case m: MethodSymbol if m.isPrimaryConstructor => m }.get.paramss.head
    val paramTypes = `type`.declarations.collectFirst { case m: MethodSymbol if m.isPrimaryConstructor => m }.get.paramss.head.map(_.typeSignature)
    val unapplyParams =
      for (i <- 0 until params.size) yield {
        val param = params(i)
        val paramType = paramTypes(i)
        val e = c.parse(s"_root_.saare.json.encode(x.${param.name.decoded})") // I don't understand why use of quasiquote here doen't work...
        q"""(${param.name.decoded}, $e)"""
      }
    val unapply = q"Map(..$unapplyParams).filterNot { case (_, v) => v == JNothing }"
    val applyParams = for (i <- 0 until params.size) yield {
      val param = params(i)
      val paramType = paramTypes(i)
      q"""_root_.saare.json.decode[$paramType](xs.getOrElse(${param.name.decoded}, JNothing)).get"""
    }
    val apply = q"$companion(..$applyParams)"
    val src = q"""
{
  val encode: $typeName => JValue = x => JObject($unapply)
  val decode: JValue => Option[$typeName] = x => x match {
    case JObject(xs) => scala.util.control.Exception.allCatch[$typeName].opt($apply)
    case _ => None
  }
  Codec[$typeName](encode = encode, decode = decode)
}
"""
    // println(src) // debug print
    c.Expr[json.Codec[A]](src)
  }
  def CaseClassCodec[A]: json.Codec[A] = macro CaseClassCodecImpl[A]
}