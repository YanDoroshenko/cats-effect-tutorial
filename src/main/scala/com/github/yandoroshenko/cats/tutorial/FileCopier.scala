package com.github.yandoroshenko.cats.tutorial

import cats.effect.{Sync, Resource}
import cats.syntax.all._
import java.io._

object FileCopier {

  def transfer[F[_] : Sync](origin: InputStream, destination: OutputStream): F[Long] =
    transmit(origin, destination, new Array[Byte](1024 * 10), 0L)

  def transmit[F[_] : Sync](origin: InputStream, destination: OutputStream, buffer: Array[Byte], acc: Long): F[Long] =
    for {
      amount <- Sync[F].blocking(origin.read(buffer, 0, buffer.size))
      count <- if (amount > -1) {
        Sync[F].blocking(destination.write(buffer, 0, amount)) >> transmit(origin, destination, buffer, acc + amount)
      } else {
        Sync[F].pure(acc)
      }
    } yield count

  def copy[F[_] : Sync](origin: File, destination: File): F[Long] =
    inputOutputStreams(origin, destination).use(transfer)

  def inputStream[F[_] : Sync](f: File): Resource[F, FileInputStream] =
    Resource.make {
      Sync[F].blocking(new FileInputStream(f))
    } { inStream =>
      Sync[F].blocking(inStream.close())
    }

  def outputStream[F[_] : Sync](f: File): Resource[F, FileOutputStream] =
    Resource.make {
      Sync[F].blocking(new FileOutputStream(f))
    } { outStream =>
      Sync[F].blocking(outStream.close())
    }

  def inputOutputStreams[F[_] : Sync](in: File, out: File): Resource[F, (FileInputStream, FileOutputStream)] =
    for {
      i <- inputStream(in)
      o <- outputStream(out)
    } yield (i, o)
}

