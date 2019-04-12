package example

import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo, JsonTypeName}
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

@JsonTypeInfo(use = Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type")
@JsonSubTypes(Array(
  new Type(value = classOf[AMagic], name="int")
))
sealed trait TRootMagic {
  val `type`: String
  val rootSeq: Seq[Any]

  def toJson: String = {
    example.TRootMagic.mapper.writeValueAsString(this)
  }
}

object TRootMagic {
  private val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.registerModule(new JavaTimeModule())
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
  mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

  //this private will block external access to this method outside of package, so yes TRoot.fromJson won't work in sbt console
  private[example] def fromJson[T <: TRootMagic](json: String): T = {
    // Due to type erasure, need to provide
    // an instance of TypeReference with all the needed Type information.
    // @see https://stackoverflow.com/a/29824075/519951
    mapper.readValue(json, new TypeReference[T] {})
  }
}

@JsonTypeName("int")
final case class AMagic(
                    override val rootSeq: Seq[Any],
                    override val `type`: String
                  ) extends TRootMagic

object AMagic {
  def fromJson(json: String): AMagic = {
    TRootMagic.fromJson[AMagic](json)
  }
}

case class MagicClass(key: String, `type`: String, values: Seq[Any])

//case class Setting(key: String, `type`: String, values: Seq[Any]) extends TRoot
