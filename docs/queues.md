# Queues
Package `org.tools4j.mmap.queue.api` contains the queue types, with `Queue.open(..)` as the main entry point.
Configuration is in `org.tools4j.mmap.queue.config`.

Main classes:
* `Queue`: a queue stored in a directory, opened as defined by an `OpenMode`; creates appenders, pollers and readers,
  and closes them when it is closed
* `Appender`: appends entries from a byte array or buffer, or zero-copy via `appending(..)` and `AppendingContext`;
  multiple appenders can append to the same queue concurrently
* `Poller`: sequential retrieval of entries via `EntryHandler` callback, which returns how to move to the next entry
  (see `Move`)
* `EntryReader`: random access to entries by index
* `EntryIterator`: iteration over entries from a start index, forward, backward or continuously (also returning
  entries appended later, like a continuous query)
* `IndexReader`: first and last index, and number of entries
* `QueueConfig`, `AppenderConfig`, `ReaderConfig` and `IndexReaderConfig`: configuration of the queue, appenders and
  readers; defaults can be set via system properties

## Concurrent Access
Appenders, pollers and readers are not thread-safe: each thread needs its own instances. Multiple appenders can
append to the same queue concurrently, and pollers and readers can read the queue while entries are being appended.

## Quick example
```java
try (Queue queue = Queue.open(new File("/tmp/myQueue"))) {

    //append: copy from a byte array or buffer, or encode in place (zero-copy) and commit
    Appender appender = queue.createAppender();
    appender.append("Hello".getBytes(StandardCharsets.US_ASCII));
    try (AppendingContext context = appender.appending(64)) {
        int length = context.buffer().putStringWithoutLengthAscii(0, "World");
        context.commit(length);
    }

    //poll: sequential retrieval via callback, the returned move determines the next entry
    Poller poller = queue.createPoller();
    while (poller.poll((index, buffer, offset, len) -> {
        //print: 0:Hello, 1:World
        System.out.println(index + ":" + buffer.getStringWithoutLengthAscii(offset, len));
        return Move.NEXT;
    }) > 0);

    //read: random access by index
    EntryReader reader = queue.createEntryReader();
    try (ReadingContext entry = reader.reading(1)) {
        if (entry.hasEntry()) {
            DirectBuffer buffer = entry.buffer();//World
        }
    }

    //iterate: forward, backward or continuously from a start index
    EntryIterator iterator = queue.createEntryIterator();
    try (IterableContext entries = iterator.readingFromLast().backward()) {
        for (Entry entry : entries) {
            DirectBuffer buffer = entry.buffer();//World, Hello
        }
    }

    //index: first and last index, size
    IndexReader indexReader = queue.createIndexReader();
    long first = indexReader.firstIndex();//0
    long last = indexReader.lastIndex();//1
    long size = indexReader.size();//2
}
```
The full example is in
[QueueIntroTest](../mmap-queue/src/test/java/org/tools4j/mmap/queue/api/QueueIntroTest.java).
