package com.github.yandoroshenko.cats.tutorial

import cats.effect.{Sync, Resource}
import cats.syntax.all._
import java.io._
import java.nio.file._

object FileCopier {

  def transfer[F[_] : Sync](origin: InputStream, destination: OutputStream, bufferSize: Int = 1024 * 10): F[Long] =
    transmit(origin, destination, new Array[Byte](bufferSize), 0L)

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
    inputOutputStreams(origin, destination).use { (i, o) => transfer(i, o) }

  def copyRecursive[F[_] : Sync](origin: File, destination: File): F[Map[String, Long]] = {
    val originPath = origin.getAbsolutePath()

    def iterate(from: File): F[Map[String, Long]] =
      if (from.isDirectory()) {
        from.listFiles().toSeq.map(iterate)
          .sequence
          .map(_.foldLeft(Map.empty[String, Long])(_ ++ _))
      } else {
        val relativePath = Paths.get(originPath).relativize(Paths.get(from.getAbsolutePath()))
        val absolutePath = Paths.get(destination.getAbsolutePath()).resolve(relativePath)
        val absoluteFile = absolutePath.toFile()

        Sync[F].blocking(absoluteFile.getParentFile().mkdirs()) >>
          copy(from, absoluteFile)
            .map(relativePath.toString -> _)
            .map {
              case _ if from.isDirectory() => Map.empty
              case p => Map(p)
            }
      }

    iterate(origin)
  }

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

