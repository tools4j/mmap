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

Closing a queue also closes the appenders, pollers and readers created through it that are still open, but it does not
stop the threads using them: closing one while another thread still uses it can crash the JVM, as its memory is
unmapped. Close the queue only once those threads have finished, with a happens-before edge such as `Thread.join()`
or a volatile flag they set when done; preferably each thread closes its own instances. Before closing any of them,
the queue warns through its error listener of each one still open that another thread created, so that the warning
precedes a crash if that thread still uses it (with a synchronous log appender).

## Entry length
An entry is stored in the payload file of its appender as a 4-byte length followed by its data, at a position that is
a multiple of 8, so that the data starts 4 bytes after a multiple of 8; applications needing 8-byte aligned data can
start their entries with a 4-byte field. Payload regions are mapped with an overlap beyond their end (see
[Region overlap](mappings.md#region-overlap)), so that an entry is accessible in one piece from the region it starts
in, also across a region end or the end of a payload file. Two entry lengths follow from the queue layout, available
through `Queue.layout()`:
* the **safe entry length**, the payload overlap size plus 4 bytes, `QueueLayoutConfig.safeEntryLength()`: an entry up
  to this length fits at every position, and it is the capacity of `Appender.appending()`
* the **max entry length**, the payload region size plus the payload overlap size minus 4 bytes,
  `QueueLayoutConfig.maxEntryLength()`, for `Appender.appending(capacity)` and the `append(..)` methods: a longer entry
  that does not fit at the next position starts at the next payload region instead, leaving the rest of the current
  region unused

The payload overlap size is part of the queue layout, stored when the queue is created: 64 KB by default (system
property `mmap.queue.payloadOverlapSize`, or `QueueConfigurator.payloadOverlapSize(..)`), limited to the payload region
size unless set explicitly, and a positive multiple of 4096 up to the payload region size. Payload files are
physically larger than the max payload file size by the overlap size.

## Quick example
```java
try (Queue queue = Queue.open(new File("/tmp/myQueue"))) {

    //append: copy from a byte array or buffer, or encode in place (zero-copy) and commit
    Appender appender = queue.createAppender();
    appender.append("Hello".getBytes(StandardCharsets.US_ASCII));
    try (AppendingContext context = appender.appending()) {
        int length = context.buffer().putTextLatin1(0, "World");
        context.commit(length);
    }

    //poll: sequential retrieval via callback, the returned move determines the next entry
    Poller poller = queue.createPoller();
    while (poller.poll((index, buffer, offset, len) -> {
        //print: 0:Hello, 1:World
        System.out.println(index + ":" + buffer.getTextLatin1(offset, len));
        return Move.NEXT;
    }) > 0);

    //read: random access by index
    EntryReader reader = queue.createEntryReader();
    try (ReadingContext entry = reader.reading(1)) {
        if (entry.hasEntry()) {
            MemoryBuffer buffer = entry.buffer();//World
        }
    }

    //iterate: forward, backward or continuously from a start index
    EntryIterator iterator = queue.createEntryIterator();
    try (IterableContext entries = iterator.readingFromLast().backward()) {
        for (Entry entry : entries) {
            MemoryBuffer buffer = entry.buffer();//World, Hello
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
