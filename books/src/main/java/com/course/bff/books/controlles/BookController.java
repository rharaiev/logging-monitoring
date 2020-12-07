package com.course.bff.books.controlles;

import brave.Span;
import brave.Tracer;
import com.course.bff.books.models.Book;
import com.course.bff.books.requests.CreateBookCommand;
import com.course.bff.books.responses.BookResponse;
import com.course.bff.books.services.BookService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("api/v1/books")
public class BookController {

    private final static Logger logger = LoggerFactory.getLogger(BookController.class);
    private final BookService bookService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    private Counter requestCounter;
    private Counter errorCounter;
    private Timer executionTimer;

    @Value("${redis.topic}")
    private String redisTopic;

    public BookController(BookService bookService, RedisTemplate<String, Object> redisTemplate, Tracer tracer, MeterRegistry meterRegistry) {
        this.bookService = bookService;
        this.redisTemplate = redisTemplate;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        initMetrics();
    }

    @GetMapping()
    public Collection<BookResponse> getBooks() throws Exception {
        return executionTimer.recordCallable(() -> {
            logger.info("Get book list");
            List<BookResponse> bookResponses = new ArrayList<>();
            this.bookService.getBooks().forEach(book -> {
                BookResponse bookResponse = createBookResponse(book);
                bookResponses.add(bookResponse);
            });

            requestCounter.increment();
            return bookResponses;
        });
    }

    @GetMapping("/{id}")
    public BookResponse getById(@PathVariable UUID id) {
        logger.info(String.format("Find book by id %s", id));
        Optional<Book> bookSearch = this.bookService.findById(id);
        if (bookSearch.isEmpty()) {
            errorCounter.increment();
            throw new RuntimeException("Book isn't found");
        }


        requestCounter.increment();
        return createBookResponse(bookSearch.get());
    }

    @PostMapping()
    public BookResponse createBooks(@RequestBody CreateBookCommand createBookCommand) {
        logger.info("Create books");
        Book book = this.bookService.create(createBookCommand);
        BookResponse authorResponse = createBookResponse(book);
        this.sendPushNotification(authorResponse);
        requestCounter.increment();
        return authorResponse;
    }

    private void sendPushNotification(BookResponse bookResponse) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Span redisSpan = tracer.nextSpan().name("redisSpan").start();
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(redisSpan.start())) {
            redisTemplate.convertAndSend(redisTopic, gson.toJson(bookResponse));
        } catch (Exception e) {
            logger.error("Push Notification Error", e);
        } finally {
            redisSpan.finish();
        }
    }

    private BookResponse createBookResponse(Book book) {
        BookResponse bookResponse = new BookResponse();
        bookResponse.setId(book.getId());
        bookResponse.setAuthorId(book.getAuthorId());
        bookResponse.setPages(book.getPages());
        bookResponse.setTitle(book.getTitle());
        return bookResponse;
    }

    private void initMetrics() {
        requestCounter = meterRegistry.counter("request_count",
                "ControllerName", this.getClass().getSimpleName(),
                "ServiceName", bookService.getClass().getSimpleName());
        errorCounter = meterRegistry.counter("error_count",
                "ControllerName", this.getClass().getSimpleName(),
                "ServiceName", bookService.getClass().getSimpleName());
        executionTimer = meterRegistry.timer("execution_duration",
                "ControllerName", this.getClass().getSimpleName(),
                "ServiceName", bookService.getClass().getSimpleName());
    }
}
