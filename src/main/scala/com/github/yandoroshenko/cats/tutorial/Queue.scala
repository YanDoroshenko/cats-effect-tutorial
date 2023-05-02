package com.github.yandoroshenko.cats.tutorial

import cats.effect._
import cats.effect.std.Console
import cats.syntax.all._
import cats.effect.syntax.all._
import scala.collection.immutable.Queue


case class State[F[_], A](consumers: Queue[Deferred[F, A]], producers: Queue[(A, Deferred[F, Unit])])

case class ConcurrentQueue[F[_]: Async, A](stateR: Ref[F, State[F, A]])

object ConcurrentQueue {
  def enqueue[F[_]: Async: Console, A](q: ConcurrentQueue[F, A])(a: A): F[Unit] =
    Deferred[F, Unit].flatMap[Unit] { producer =>
      Console[F].println(s"Enqueue: $a") >>
      Async[F].uncancelable { poll =>
        q.stateR.modify {
          case State(c, p) if c.nonEmpty =>
            val (consumer, rest) = c.dequeue
            State(rest, p) -> consumer.complete(a).void
          case State(c, p) =>
            State(c, p.enqueue(a -> producer)) -> Async[F].unit
        }.flatten
      }
    }

  def dequeue[F[_]: Async, A](q: ConcurrentQueue[F, A]): F[A] = Deferred[F, A].flatMap[A] { consumer =>
    Async[F].uncancelable { poll =>
      q.stateR.modify {
        case State(c, p) if p.nonEmpty =>
          val ((a, release), rest) = p.dequeue
          State(c, rest) -> release.complete(()).as(a)
        case State(c, p) =>
          val cleanup = q.stateR.update { s => s.copy(consumers = s.consumers.filterNot(_ == consumer)) }
          State(c.enqueue(consumer), p) -> poll(consumer.get).onCancel(cleanup)
      }.flatten
    }
  }
}

object Queue {
  case class State[F[_], A](queue: Queue[A], capacity: Int, takers: Queue[Deferred[F,A]], offerers: Queue[(A, Deferred[F,Unit])])

  def producer[F[_]: Async: Console](id: Int, counterR: Ref[F, Int], stateR: Ref[F, State[F,Int]]): F[Unit] = {
    def offer(i: Int): F[Unit] =
      Deferred[F, Unit].flatMap[Unit]{ offerer =>
        Async[F].uncancelable { poll => // `poll` used to embed cancelable code, i.e. the call to `offerer.get`
          stateR.modify {
            case State(queue, capacity, takers, offerers) if takers.nonEmpty =>
              val (taker, rest) = takers.dequeue
              State(queue, capacity, rest, offerers) -> taker.complete(i).void
            case State(queue, capacity, takers, offerers) if queue.size < capacity =>
              State(queue.enqueue(i), capacity, takers, offerers) -> Async[F].unit
            case State(queue, capacity, takers, offerers) =>
              val cleanup = stateR.update { s => s.copy(offerers = s.offerers.filter(_._2 ne offerer)) }
              State(queue, capacity, takers, offerers.enqueue(i -> offerer)) -> poll(offerer.get).onCancel(cleanup)
          }.flatten
        }
      }

    for {
      i <- counterR.getAndUpdate(_ + 1)
      _ <- offer(i)
      _ <- if(i % 10000 == 0) Console[F].println(s"Producer $id has reached $i items") else Async[F].unit
      _ <- producer(id, counterR, stateR)
    } yield ()
  }

  def consumer[F[_]: Async: Console](id: Int, stateR: Ref[F, State[F, Int]]): F[Unit] = {
    val take: F[Int] =
      Deferred[F, Int].flatMap { taker =>
        Async[F].uncancelable { poll =>
          stateR.modify {
            case State(queue, capacity, takers, offerers) if queue.nonEmpty && offerers.isEmpty =>
              val (i, rest) = queue.dequeue
              State(rest, capacity, takers, offerers) -> Async[F].pure(i)
            case State(queue, capacity, takers, offerers) if queue.nonEmpty =>
              val (i, rest) = queue.dequeue
              val ((move, release), tail) = offerers.dequeue
              State(rest.enqueue(move), capacity, takers, tail) -> release.complete(()).as(i)
            case State(queue, capacity, takers, offerers) if offerers.nonEmpty =>
              val ((i, release), rest) = offerers.dequeue
              State(queue, capacity, takers, rest) -> release.complete(()).as(i)
            case State(queue, capacity, takers, offerers) =>
              val cleanup = stateR.update { s => s.copy(takers = s.takers.filter(_ ne taker)) }
              State(queue, capacity, takers.enqueue(taker), offerers) -> poll(taker.get).onCancel(cleanup)
          }.flatten
        }
      }

    for {
      i <- take
      _ <- if(i % 10000 == 0) Console[F].println(s"Consumer $id has reached $i items") else Async[F].unit
      _ <- consumer(id, stateR)
    } yield ()
  }
}
