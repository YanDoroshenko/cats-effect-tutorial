package com.github.yandoroshenko.cats.tutorial

import cats.effect._
import java.io.File

object MainApp extends IOApp {

  override def run(args: List[String]): IO [ExitCode] =
    for {
      _ <- if (args.length < 2) IO.raiseError(new IllegalArgumentException("Specify origin and destination file names")) else IO.unit
      origin = new File(args(0))
      _ <- if (!origin.canRead()) IO.raiseError(new IllegalArgumentException("Origin file is not readable!")) else IO.unit
      destination = new File (args(1))
      _ <- if (origin == destination) IO.raiseError(new IllegalArgumentException("Origin and destination are the the same file!")) else IO.unit
      execute <- if (destination.exists()) {
        for {
          _ <- IO.println("Destination file exists, overwrite? y/N")
          str <- IO.readLine
        } yield str.equalsIgnoreCase("y")
      } else IO.pure(true)
      _ <- if ((destination.isDirectory() || destination.exists()) && !destination.canWrite()) IO.raiseError(new IllegalArgumentException("Destination file is not writeable!")) else IO.unit
      _ <- if (execute) FileCopier.copyRecursive[IO](origin, destination).flatMap { count =>
        IO.println(s"Copied $count bytes from ${origin.getPath} to ${destination.getPath}")
      } else IO.println("Aborting")
    } yield ExitCode.Success
}
