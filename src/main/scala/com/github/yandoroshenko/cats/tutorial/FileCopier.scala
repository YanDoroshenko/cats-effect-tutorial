package com.github.yandoroshenko.cats.tutorial

import cats.effect.{IO, Resource}
import java.io._

object FileCopier {

  def transfer(origin: InputStream, destination: OutputStream): IO[Long] = ???

  def copy(origin: File, destination: File): IO[Long] =
    inputOutputStreams(origin, destination).use(transfer)

  def inputStream(f: File): Resource[IO, FileInputStream] =
    Resource.make {
      IO.blocking(new FileInputStream(f))
    } { inStream =>
      IO.blocking(inStream.close()).handleErrorWith(_ => IO.unit)
    }

  def outputStream(f: File): Resource[IO, FileOutputStream] =
    Resource.make {
      IO.blocking(new FileOutputStream(f))
    } { outStream =>
      IO.blocking(outStream.close()).handleErrorWith(_ => IO.unit)
    }

  def inputOutputStreams(in: File, out: File): Resource[IO, (FileInputStream, FileOutputStream)] =
    for {
      i <- inputStream(in)
      o <- outputStream(out)
    } yield (i, o)
}

