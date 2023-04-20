package com.github.yandoroshenko.cats.tutorial

import cats.effect._
import java.io.File

object MainApp extends IOApp {

  override def run(args: List[String]): IO [ExitCode] =
    for {
      _ <- if (args.length < 2) IO.raiseError(new IllegalArgumentException("Specify origin and destination file names")) else IO.unit
      origin = new File(args(0))
      destination = new File (args(1))
      count <- FileCopier.copy(origin, destination)
      _ <- IO.println(s"Copied $count bytes from ${origin.getPath} to ${destination.getPath}")
    } yield ExitCode.Success
}
