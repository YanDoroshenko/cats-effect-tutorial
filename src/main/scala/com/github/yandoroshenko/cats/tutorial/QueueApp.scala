package com.github.yandoroshenko.cats.tutorial

import cats.effect._
import cats.syntax.all._
import scala.collection.immutable.Queue

object QueueApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = for {
    stateR <- Ref.of[IO, State[IO, Int]](State[IO, Int](Queue.empty, Queue.empty))
    q = ConcurrentQueue[IO, Int](stateR)
    _ <- (1 to 10).toList.map(ConcurrentQueue.enqueue[IO, Int](q)).sequence.start
    _ <- (1 to 10).toList.map(_ => ConcurrentQueue.dequeue[IO, Int](q).flatMap(i => IO.println(s"Dequeue: $i"))).sequence
    res <- IO.pure(ExitCode.Success)
  } yield res
}
