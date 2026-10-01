package com.example.marketplace.interfaces.api.rest;

import com.example.marketplace.application.command.CreateProductCommand;
import com.example.marketplace.application.command.CreateProductCommandResult;
import com.example.marketplace.application.command.DeleteProductCommand;
import com.example.marketplace.application.command.UpdateProductCommand;
import com.example.marketplace.application.command.UpdateProductCommandResult;
import com.example.marketplace.application.interfaces.ProductService;
import com.example.marketplace.application.query.GetAllProductsQueryResult;
import com.example.marketplace.application.query.GetProductByIdQuery;
import com.example.marketplace.application.query.GetProductByIdQueryResult;
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
@RequestMapping("/api/v1/products")
class ProductController {

    private static final String INVALID_PRODUCT_ID = "Invalid product Id format";

    private final ProductService service;

    ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Object> createProduct(@RequestBody CreateProductRequest request, HttpServletRequest http) {
        Optional<CreateProductCommand> command = request.toCreateProductCommand();
        if (command.isEmpty()) {
            // A bad seller_id gets the product id's message; clients depend on the exact text.
            return RestErrors.error(HttpStatus.BAD_REQUEST, INVALID_PRODUCT_ID);
        }

        CreateProductCommandResult result;
        try {
            result = service.createProduct(
                    command.get().withIdempotencyKey(IdempotencyKeys.resolve(http, request.idempotencyKey())));
        } catch (RuntimeException e) {
            return RestErrors.commandError(e, "Failed to create product");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponseMapper.toProductResponse(result.result()));
    }

    @GetMapping
    ResponseEntity<Object> getAllProducts() {
        GetAllProductsQueryResult products;
        try {
            products = service.findAllProducts();
        } catch (RuntimeException e) {
            return RestErrors.serverError(e, "Failed to fetch products");
        }

        return ResponseEntity.ok(ProductResponseMapper.toProductListResponse(products.result()));
    }

    @GetMapping("/{id}")
    ResponseEntity<Object> getProductById(@PathVariable UUID id) {
        Optional<GetProductByIdQueryResult> product;
        try {
            product = service.findProductById(new GetProductByIdQuery(id));
        } catch (RuntimeException e) {
            return RestErrors.serverError(e, "Failed to fetch product");
        }

        return product
                .<ResponseEntity<Object>>map(found -> ResponseEntity.ok(ProductResponseMapper.toProductResponse(found.result())))
                .orElseGet(() -> RestErrors.error(HttpStatus.NOT_FOUND, "Product not found"));
    }

    /** The path id is converted before the body is read, so a bad id wins over a bad body. */
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Object> updateProduct(@PathVariable UUID id, @RequestBody UpdateProductRequest request,
            HttpServletRequest http) {
        Optional<UpdateProductCommand> command = request.toUpdateProductCommand(id);
        if (command.isEmpty()) {
            return RestErrors.error(HttpStatus.BAD_REQUEST, "Invalid seller Id format");
        }

        UpdateProductCommandResult result;
        try {
            result = service.updateProduct(
                    command.get().withIdempotencyKey(IdempotencyKeys.resolve(http, request.idempotencyKey())));
        } catch (RuntimeException e) {
            return RestErrors.commandError(e, "Failed to update product");
        }

        return ResponseEntity.ok(ProductResponseMapper.toProductResponse(result.result()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Object> deleteProduct(@PathVariable UUID id, HttpServletRequest http) {
        try {
            service.deleteProduct(new DeleteProductCommand(IdempotencyKeys.resolve(http, ""), id));
        } catch (RuntimeException e) {
            return RestErrors.commandError(e, "Failed to delete product");
        }

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Object> invalidId() {
        return RestErrors.error(HttpStatus.BAD_REQUEST, INVALID_PRODUCT_ID);
    }
}
