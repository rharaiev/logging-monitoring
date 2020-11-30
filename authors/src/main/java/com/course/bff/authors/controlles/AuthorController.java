package com.course.bff.authors.controlles;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import com.course.bff.authors.models.Author;
import com.course.bff.authors.requests.CreateAuthorCommand;
import com.course.bff.authors.responses.AuthorResponse;
import com.course.bff.authors.services.AuthorService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/authors")
public class AuthorController {

    @Value("${redis.topic}")
    private String redisTopic;

    private final static Logger logger = LoggerFactory.getLogger(AuthorController.class);
    private final AuthorService authorService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Tracer tracer;

    public AuthorController(AuthorService authorService, RedisTemplate<String, Object> redisTemplate, Tracer tracer) {
        this.authorService = authorService;
        this.redisTemplate = redisTemplate;
        this.tracer = tracer;
    }

    @GetMapping()
    public Collection<AuthorResponse> getAuthors() {
        logger.info("Get authors");
        List<AuthorResponse> authorResponses = new ArrayList<>();
        this.authorService.getAuthors().forEach(author -> {
            AuthorResponse authorResponse = createAuthorResponse(author);
            authorResponses.add(authorResponse);
        });

        return authorResponses;
    }

    @GetMapping("/{id}")
    public AuthorResponse getById(@PathVariable UUID id) {
        logger.info(String.format("Find authors by %s", id));
        Optional<Author> authorSearch = this.authorService.findById(id);
        if (authorSearch.isEmpty()) {
            throw new RuntimeException("Author isn't found");
        }

        return createAuthorResponse(authorSearch.get());
    }

    @PostMapping()
    public AuthorResponse createAuthors(@RequestBody CreateAuthorCommand createAuthorCommand) {
        logger.info("Create authors");
        Author author = this.authorService.create(createAuthorCommand);
        AuthorResponse authorResponse = createAuthorResponse(author);
        this.sendPushNotification(authorResponse);
        return authorResponse;
    }


    private void sendPushNotification(AuthorResponse authorResponse) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Span redisSpan = tracer.nextSpan().name("redisSpan").start();
        try(SpanInScope ws = tracer.withSpanInScope(redisSpan.start())) {
            redisTemplate.convertAndSend(redisTopic, gson.toJson(authorResponse));
        } catch (Exception e) {
            logger.error("Push Notification Error", e);
        } finally {
            redisSpan.finish();
        }
    }

    private AuthorResponse createAuthorResponse(Author author) {
        AuthorResponse authorResponse = new AuthorResponse();
        authorResponse.setId(author.getId());
        authorResponse.setFirstName(author.getFirstName());
        authorResponse.setLastName(author.getLastName());
        authorResponse.setAddress(author.getAddress());
        authorResponse.setLanguage(author.getLanguage());
        return authorResponse;
    }
}
