package com.twitter.finagle.httpx

import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.util.{Await, Future}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HttpMuxerTest extends FunSuite {
  // todo: add other metrics when they are supported
  class DummyService(reply: String) extends Service[Request, Response] {
    def apply(request: Request): Future[Response] = {
      val response = Response(Version.Http11, Status.Ok)
      response.content = Buf.Utf8(reply)
      Future.value(response)
    }
  }

  val (fooBarPrefix, fooBarExact, fooBooBaz, exactMatch, specialCase) =
      ("fooBarPrefix", "fooBarExact", "fooBooBaz", "exactMatch", "specialCase")

  val muxService = new HttpMuxer()
    .withHandler("foo/bar/", new DummyService(fooBarPrefix)) // prefix match
    .withHandler("foo/bar", new DummyService(fooBarExact))  // exact match -- not shadowed by foo/bar/
    .withHandler("foo/boo/baz/", new DummyService(fooBooBaz))
    .withHandler("exact/match", new DummyService(exactMatch)) // exact match

  test("handles params properly") {
    assert(Response(Await.result(muxService(Request("/foo/bar/blah?j={}")))).contentString === fooBarPrefix)
  }

  test("prefix matching is handled correctly") {
    assert(Response(Await.result(muxService(Request("/fooblah")))).status === Status.NotFound)

    assert(Response(Await.result(muxService(Request("/foo/bar/blah")))).contentString === fooBarPrefix)

    assert(Response(Await.result(muxService(Request("/foo//bar/blah")))).contentString === fooBarPrefix)

    assert(Response(Await.result(muxService(Request("/foo/bar")))).contentString === fooBarExact)

    assert(Response(Await.result(muxService(Request("/foo/bar/")))).contentString === fooBarPrefix)

    assert(Response(Await.result(muxService(Request("/foo/boo/baz")))).status === Status.NotFound)

    assert(Response(Await.result(muxService(Request("/foo/boo/baz/blah")))).contentString === fooBooBaz)

    assert(Response(Await.result(muxService(Request("/foo/barblah")))).status === Status.NotFound)
  }

  test("exact matching is handled correctly") {
    assert(Response(Await.result(muxService(Request("/exact/match")))).contentString === exactMatch)

    assert(Response(Await.result(muxService(Request("/exact/match/")))).status === Status.NotFound)

    assert(Response(Await.result(muxService(Request("/exact/match/nested")))).status === Status.NotFound)
  }

  test("""special cases "" and "/" are handled correctly""") {
    val slashMux = new HttpMuxer().withHandler("/", new DummyService(specialCase))
    assert(Response(Await.result(slashMux(Request("/")))).contentString === specialCase)
    assert(Response(Await.result(slashMux(Request("")))).status === Status.NotFound)
    assert(Response(Await.result(slashMux(Request("/anything")))).contentString === specialCase)

    val emptyStringMux = new HttpMuxer().withHandler("", new DummyService(specialCase))
    assert(Response(Await.result(emptyStringMux(Request("/")))).contentString === specialCase)
    assert(Response(Await.result(emptyStringMux(Request("")))).contentString === specialCase)
    assert(Response(Await.result(emptyStringMux(Request("/anything")))).status === Status.NotFound)
  }

  test("Registering a service with an existing name will overwrite the old") {
    val (origResp, newResp) = ("orig", "new")
    val orig = new HttpMuxer().withHandler("foo/", new DummyService(origResp))
    val overridden = orig.withHandler("foo/", new DummyService(newResp))

    assert(Response(Await.result(orig(Request("/foo/bar")))).contentString === origResp)
    assert(Response(Await.result(overridden(Request("/foo/bar")))).contentString === newResp)
  }
}
