package com.example.marketplace.interfaces.api.rest;

import com.example.marketplace.application.command.CreateSellerCommandResult;
import com.example.marketplace.application.command.DeleteSellerCommand;
import com.example.marketplace.application.command.UpdateSellerCommand;
import com.example.marketplace.application.command.UpdateSellerCommandResult;
import com.example.marketplace.application.interfaces.SellerService;
import com.example.marketplace.application.query.GetAllSellersQueryResult;
import com.example.marketplace.application.query.GetSellerByIdQuery;
import com.example.marketplace.application.query.GetSellerByIdQueryResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/v1/sellers")
class SellerController {

    private static final String INVALID_SELLER_ID = "Invalid seller Id format";

    private final SellerService service;

    SellerController(SellerService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Object> createSeller(@RequestBody CreateSellerRequest request, HttpServletRequest http) {
        CreateSellerCommandResult result;
        try {
            result = service.createSeller(request.toCreateSellerCommand()
                    .withIdempotencyKey(IdempotencyKeys.resolve(http, request.idempotencyKey())));
        } catch (RuntimeException e) {
            return RestErrors.commandError(e, "Failed to create seller");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(SellerResponseMapper.toSellerResponse(result.result()));
    }

    @GetMapping
    ResponseEntity<Object> getAllSellers() {
        GetAllSellersQueryResult sellers;
        try {
            sellers = service.findAllSellers();
        } catch (RuntimeException e) {
            return RestErrors.serverError(e, "Failed to fetch sellers");
        }

        return ResponseEntity.ok(SellerResponseMapper.toSellerListResponse(sellers.result()));
    }

    @GetMapping("/{id}")
    ResponseEntity<Object> getSellerById(@PathVariable UUID id) {
        Optional<GetSellerByIdQueryResult> seller;
        try {
            seller = service.findSellerById(new GetSellerByIdQuery(id));
        } catch (RuntimeException e) {
            return RestErrors.serverError(e, "Failed to fetch seller");
        }

        return seller
                .<ResponseEntity<Object>>map(found -> ResponseEntity.ok(SellerResponseMapper.toSellerResponse(found.result())))
                .orElseGet(() -> RestErrors.error(HttpStatus.NOT_FOUND, "Seller not found"));
    }

    /** The seller id travels in the body, as the API spec documents. */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Object> putSeller(@RequestBody UpdateSellerRequest request, HttpServletRequest http) {
        Optional<UpdateSellerCommand> command = request.toUpdateSellerCommand();
        if (command.isEmpty()) {
            return RestErrors.error(HttpStatus.BAD_REQUEST, RestErrors.UNREADABLE_BODY);
        }

        UpdateSellerCommandResult result;
        try {
            result = service.updateSeller(
                    command.get().withIdempotencyKey(IdempotencyKeys.resolve(http, request.idempotencyKey())));
        } catch (RuntimeException e) {
            return RestErrors.commandError(e, "Failed to update seller");
        }

        return ResponseEntity.ok(SellerResponseMapper.toSellerResponse(result.result()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Object> deleteSeller(@PathVariable UUID id, HttpServletRequest http) {
        try {
            service.deleteSeller(new DeleteSellerCommand(IdempotencyKeys.resolve(http, ""), id));
        } catch (RuntimeException e) {
            return RestErrors.commandError(e, "Failed to delete seller");
        }

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Object> invalidId() {
        return RestErrors.error(HttpStatus.BAD_REQUEST, INVALID_SELLER_ID);
    }
}
