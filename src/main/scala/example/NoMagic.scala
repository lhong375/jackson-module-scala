/*

medium post for this:
https://medium.com/@linda0511ny/polymorphism-and-generic-type-with-jackson-scala-5f79872b1d23

This example has 1 root trait, 3 child trait (Int/Float/String) extends the root one.
root trait define a Seq of generic type, each child trait override it with a type int/float/string.

Annotation JsonTypeInfo/JsonSubTypes/JsonTypeName ensure the proper type of child trait is created during decoding.
All 3 child traits are properly hook up.

For generic type however, only the Int child one is properly configured. After trying out "have some fun" part, you will see what/why

\\\\\\\\\\\\\\\\\\\\\\\\\\ CODE \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\


`sbt console`
tip: `:paste` to get into paste mode, then control+D to get out

import example._
val sc = StringChild(value="a", valueSeq=Seq("a","b","c"))
val ic = IntChild(value=100, valueSeq=Seq(1,2,3))
val scjson = sc.toJson
val scjson2 = JsonUtils.toJson(sc) // {"value":"a","valueSeq":["a","b","c"],"type":"string"}
val icjson = ic.toJson
val icjson2 = JsonUtils.toJson(ic) // {"value":100,"valueSeq":[1,2,3],"type":"int"}

//this is straight forward
JsonUtils.fromJson[StringChild](scjson)

//this works because we register name="string" with @JsonSubTypes
JsonUtils.fromJson[Root](scjson)

//cast StringChild json string to IntChild, will error out with java.lang.IllegalArgumentException
JsonUtils.fromJson[IntChild](scjson)

//////////////////// Time to have some fun ////////////////////

//notice a number 100 in a suppose-to-be string seq
val badscjson=s"""{"type":"string","value":"a","valueSeq":["a","b",100]}"""

//will this work ?
val badsc =  JsonUtils.fromJson[Root](badscjson)

//It give us StringChild(a,List(a, b, 100)), then what's the type of 100 in a list of string ?
badsc.valueSeq  //it become Seq[Any], type is GONE
badsc.valueSeq.last //Any

//how about ask it to decode into StringChild instead of Root?
val badsc2 =  JsonUtils.fromJson[StringChild](badscjson)
badsc2.valueSeq //at least we get Seq[String]
badsc2.valueSeq.last //String = 100

//Guess cast Int into String is somewhat reasonable (?), how about String to Float ?

val badfcjson=s"""{"type":"float","value":1.1,"valueSeq":["a","b",1.1]}"""
//badfcjson: String = {"type":"float","value":1.1,"valueSeq":["a","b",1.1]}

FloatChild.fromJson(badfcjson)
//res0: example.FloatChild = FloatChild(1.1,List(a, b, 1.1))

//Will this work ?
val badfc1 = FloatChild.fromJson(badfcjson)
//badfc1: example.FloatChild = FloatChild(1.1,List(a, b, 1.1))

//A seq of Float with 2 string in it?
badfc1.valueSeq
//res1: Seq[Float] = List(a, b, 1.1)

//But ... it will error out when you try to get it.
badfc1.valueSeq.last
//java.lang.ClassCastException: java.lang.String cannot be cast to java.lang.Float

//So, same thing.

////////////////////////////////// IntChild has the fix /////////////////////////////

//notice the @JsonDeserialize(contentAs = classOf[java.lang.Integer]) in IntChild
val badicjson=s"""{"type":"int","value":1,"valueSeq":["a",2,3]}"""

//will this work ?
val badic =  JsonUtils.fromJson[Root](badicjson)

//finally, above error out : InvalidFormatException: Cannot deserialize value of type `java.lang.Integer` from String "a": not a valid Integer value

*/

package example

import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo, JsonTypeName}
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper



@JsonTypeInfo(use = Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type")
@JsonSubTypes(Array(
  //name="int" here will make sure when JsonUtils.fromJson see "type":"int", it will make a IntChild object.
  new Type(value = classOf[IntChild], name="int"),
  new Type(value = classOf[StringChild], name="string"),
  new Type(value = classOf[FloatChild], name="float")
))
sealed trait Root {
  val value: Any
  val valueSeq: Seq[Any]

  def toJson: String = {
    example.Root.mapper.writeValueAsString(this)
  }
}

object Root {
  private val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.registerModule(new JavaTimeModule())
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
  mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

  //this private will block external access to this method outside of package, so yes, Root.fromJson won't work in sbt console
  private[example] def fromJson[T <: Root](json: String): T = {
    // Due to type erasure, need to provide
    // an instance of TypeReference with all the needed Type information.
    // @see https://stackoverflow.com/a/29824075/519951
    mapper.readValue(json, new TypeReference[T] {})
  }
}

//JsonTypeName with "string" will make sure toJson output "type":"string"
@JsonTypeName("string")
final case class StringChild(override val value: String, override val valueSeq: Seq[String]) extends Root

object StringChild {
  def fromJson(json: String): StringChild = {
    Root.fromJson[StringChild](json)
  }
}

@JsonTypeName("int")
final case class IntChild(override val value: Int,
                         //This is necessary to force type check o valueSeq
                          @JsonDeserialize(contentAs = classOf[java.lang.Integer])
                          override val valueSeq: Seq[Int]) extends Root

object IntChild {
  def fromJson(json: String): IntChild = {
    Root.fromJson[IntChild](json)
  }
}

@JsonTypeName("float")
final case class FloatChild(override val value: Float,
                          override val valueSeq: Seq[Float]) extends Root

object FloatChild {
  def fromJson(json: String): FloatChild = {
    Root.fromJson[FloatChild](json)
  }
}
