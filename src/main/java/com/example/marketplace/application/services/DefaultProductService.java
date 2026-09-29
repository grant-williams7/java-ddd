package com.example.marketplace.application.services;

import com.example.marketplace.application.command.CreateProductCommand;
import com.example.marketplace.application.command.CreateProductCommandResult;
import com.example.marketplace.application.command.DeleteProductCommand;
import com.example.marketplace.application.command.DeleteProductCommandResult;
import com.example.marketplace.application.command.UpdateProductCommand;
import com.example.marketplace.application.command.UpdateProductCommandResult;
import com.example.marketplace.application.interfaces.ProductService;
import com.example.marketplace.application.query.GetAllProductsQueryResult;
import com.example.marketplace.application.query.GetProductByIdQuery;
import com.example.marketplace.application.query.GetProductByIdQueryResult;
import com.example.marketplace.domain.entities.Money;
import com.example.marketplace.domain.entities.Product;
import com.example.marketplace.domain.entities.ProductNotFoundException;
import com.example.marketplace.domain.entities.SellerNotFoundException;
import com.example.marketplace.domain.entities.ValidatedProduct;
import com.example.marketplace.domain.entities.ValidatedSeller;
import com.example.marketplace.domain.repositories.ProductRepository;
import com.example.marketplace.domain.repositories.SellerRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class DefaultProductService implements ProductService {

    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;
    private final Idempotency idempotency;

    DefaultProductService(ProductRepository productRepository, SellerRepository sellerRepository,
            Idempotency idempotency) {
        this.productRepository = productRepository;
        this.sellerRepository = sellerRepository;
        this.idempotency = idempotency;
    }

    @Override
    public CreateProductCommandResult createProduct(CreateProductCommand command) {
        return idempotency.withIdempotency(command.idempotencyKey(), command, CreateProductCommandResult.class, () -> {
            ValidatedSeller seller = findValidatedSeller(command.sellerId());
            Money price = new Money(command.priceMinorUnits(), command.currency());

            ValidatedProduct product = ValidatedProduct.of(Product.create(command.name(), price, seller));
            productRepository.create(product);

            return new CreateProductCommandResult(ProductResultMapper.fromValidatedEntity(product));
        });
    }

    @Override
    public GetAllProductsQueryResult findAllProducts() {
        return new GetAllProductsQueryResult(productRepository.findAll().stream()
                .map(ProductResultMapper::fromEntity)
                .toList());
    }

    @Override
    public Optional<GetProductByIdQueryResult> findProductById(GetProductByIdQuery query) {
        return productRepository.findById(query.id())
                .map(product -> new GetProductByIdQueryResult(ProductResultMapper.fromEntity(product)));
    }

    @Override
    public UpdateProductCommandResult updateProduct(UpdateProductCommand command) {
        return idempotency.withIdempotency(command.idempotencyKey(), command, UpdateProductCommandResult.class, () -> {
            Product existing = productRepository.findById(command.id()).orElseThrow(ProductNotFoundException::new);

            if (!Objects.equals(command.sellerId(), existing.getSellerId())) {
                existing.assignSeller(findValidatedSeller(command.sellerId()));
            }

            existing.updateName(command.name());
            existing.updatePrice(new Money(command.priceMinorUnits(), command.currency()));

            ValidatedProduct product = ValidatedProduct.of(existing);
            productRepository.update(product);

            return new UpdateProductCommandResult(ProductResultMapper.fromValidatedEntity(product));
        });
    }

    @Override
    public DeleteProductCommandResult deleteProduct(DeleteProductCommand command) {
        return idempotency.withIdempotency(command.idempotencyKey(), command, DeleteProductCommandResult.class, () -> {
            productRepository.findById(command.id()).orElseThrow(ProductNotFoundException::new);
            productRepository.delete(command.id());

            return new DeleteProductCommandResult(true);
        });
    }

    private ValidatedSeller findValidatedSeller(UUID sellerId) {
        return ValidatedSeller.of(sellerRepository.findById(sellerId).orElseThrow(SellerNotFoundException::new));
    }
}
