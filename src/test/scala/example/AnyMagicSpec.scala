package example

import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.FlatSpec
import example.JsonUtils._

class AnyMagicSpec extends FlatSpec with LazyLogging {

  it should "Seq[Any] will take anything, parse as it is" in {

    val mcstr = s"""{"key":"1","type":"int","values":["a","b"]}"""
    val mc = fromJson[MagicClass](mcstr)
    assertResult("int")(mc.`type`)
    assert(mc.values.head.isInstanceOf[String])

  }
}
