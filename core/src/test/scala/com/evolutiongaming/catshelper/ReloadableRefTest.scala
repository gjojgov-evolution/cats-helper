package com.evolutiongaming.catshelper

import cats.effect.{Deferred, IO}
import cats.syntax.all.*
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

class ReloadableRefTest extends AsyncFunSuite with Matchers {

  import com.evolutiongaming.catshelper.IOSuite.*

  test("ReloadableRef.of is non-blocking") {
    val result = for {
      l <- Deferred[IO, Unit]
      _ <- ReloadableRef
        .of[IO, Unit](l.get, 1.minute)
        .use { _ =>
          IO { succeed }
        }
    } yield {}
    result.run(1.second)
  }

  test("ReloadableRef#get is semantically blocking") {
    val result = for {
      l <- Deferred[IO, Unit]
      d <- Deferred[IO, Unit]
      f <- ReloadableRef
        .of[IO, Unit](l.get, 1.minute)
        .use { ref =>
          ref.get >> d.complete({})
        }
        .start
      _ <- d.tryGet.map { opt =>
        opt shouldBe none[Unit]
      }
      _ <- l.complete({})
      _ <- f.join
    } yield {}
    result.run(1.second)
  }

  test("reload value after expiration") {
    val result = for {
      v <- IO.ref(0)
      l = IO.sleep(10.millis) >> v.get
      _ <- ReloadableRef
        .of[IO, Int](l, 100.millis)
        .use { ref =>
          for {
            v0 <- ref.get
            _ <- IO { v0 shouldBe 0 }
            _ <- v.set(1)
            _ <- IO.sleep(100.millis)
            _ <- ref.get.map { v1 =>
              v1 shouldBe 1
            }.eventually()
          } yield {}
        }
    } yield {}
    result.run(1.second)
  }

  test("raise exception if reload fails") {
    val result = for {
      v <- IO.ref(none[Throwable])
      l = IO.sleep(10.millis) >> v.get.flatMap(
        _.traverse(_.raiseError[IO, Unit]).void
      )
      _ <- ReloadableRef
        .of[IO, Unit](l, 100.millis)
        .use { ref =>
          val err = new RuntimeException("test exception")
          for {
            _ <- ref.get
            _ <- v.set(err.some)
            _ <- IO.sleep(100.millis)
            _ <- ref.get.attempt.map { e =>
              e shouldBe err.asLeft[Unit]
            }.eventually()
          } yield {}
        }
    } yield {}
    result.run(1.second)
  }

}
