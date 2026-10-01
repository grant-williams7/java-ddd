package com.example.marketplace.application.services;

import static com.example.marketplace.application.services.ServiceTestSupport.createProductCommand;
import static com.example.marketplace.application.services.ServiceTestSupport.createSellerCommand;
import static com.example.marketplace.application.services.ServiceTestSupport.persistedSeller;
import static com.example.marketplace.application.services.ServiceTestSupport.productService;
import static com.example.marketplace.application.services.ServiceTestSupport.sellerService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.marketplace.application.command.CreateProductCommand;
import com.example.marketplace.application.command.CreateProductCommandResult;
import com.example.marketplace.application.command.CreateSellerCommand;
import com.example.marketplace.application.command.CreateSellerCommandResult;
import com.example.marketplace.application.command.DeleteProductCommand;
import com.example.marketplace.application.command.DeleteProductCommandResult;
import com.example.marketplace.application.command.DeleteSellerCommand;
import com.example.marketplace.application.command.DeleteSellerCommandResult;
import com.example.marketplace.application.command.UpdateProductCommand;
import com.example.marketplace.application.command.UpdateProductCommandResult;
import com.example.marketplace.application.command.UpdateSellerCommand;
import com.example.marketplace.application.command.UpdateSellerCommandResult;
import com.example.marketplace.application.query.GetSellerByIdQuery;
import com.example.marketplace.domain.entities.Currency;
import com.example.marketplace.domain.entities.Money;
import com.example.marketplace.domain.entities.ProductNotFoundException;
import com.example.marketplace.domain.entities.Seller;
import com.example.marketplace.domain.entities.SellerNotFoundException;
import com.example.marketplace.domain.entities.ValidatedSeller;
import com.example.marketplace.domain.entities.ValidationException;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FailurePathsTest {

    @Nested
    class ProductServiceErrorPaths {

        private final FakeProductRepository products = new FakeProductRepository();
        private final FakeSellerRepository sellers = new FakeSellerRepository();
        private final DefaultProductService service = productService(products, sellers);

        private UUID createdProductId(UUID sellerId) {
            return service.createProduct(createProductCommand("Widget", 999, sellerId)).result().id();
        }

        @Test
        void createProduct_sellerNotFound() {
            assertThatThrownBy(() -> service.createProduct(createProductCommand("Widget", 999, UUID.randomUUID())))
                    .isInstanceOf(SellerNotFoundException.class)
                    .hasMessage("seller not found");
        }

        @Test
        void createProduct_invalidCurrency() {
            UUID sellerId = persistedSeller(sellers).seller().getId();

            assertThatThrownBy(() -> service.createProduct(
                    new CreateProductCommand("", null, "Widget", 999, new Currency("XXX"), sellerId)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("unsupported currency \"XXX\"");
        }

        @Test
        void updateProduct_notFound() {
            assertThatThrownBy(() -> service.updateProduct(new UpdateProductCommand(
                    "", UUID.randomUUID(), "Widget", 999, Currency.USD, UUID.randomUUID())))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessage("product not found");
        }

        @Test
        void updateProduct_validationError() {
            UUID sellerId = persistedSeller(sellers).seller().getId();
            UUID productId = createdProductId(sellerId);

            // An empty name must fail domain validation.
            assertThatThrownBy(() -> service.updateProduct(
                    new UpdateProductCommand("", productId, "", 999, Currency.USD, sellerId)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name must not be empty");
        }

        @Test
        void updateProduct_success() {
            UUID sellerId = persistedSeller(sellers).seller().getId();
            UUID productId = createdProductId(sellerId);

            UpdateProductCommandResult updated = service.updateProduct(
                    new UpdateProductCommand("", productId, "Widget v2", 1999, Currency.USD, sellerId));

            assertThat(updated.result().name()).isEqualTo("Widget v2");
            assertThat(updated.result().price()).isEqualTo(new Money(1999, Currency.USD));
        }

        @Test
        void deleteProduct_notFound() {
            assertThatThrownBy(() -> service.deleteProduct(new DeleteProductCommand("", UUID.randomUUID())))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessage("product not found");
        }

        @Test
        void deleteProduct_success() {
            UUID sellerId = persistedSeller(sellers).seller().getId();
            UUID productId = createdProductId(sellerId);

            DeleteProductCommandResult result = service.deleteProduct(new DeleteProductCommand("", productId));

            assertThat(result.success()).isTrue();
            assertThat(products.products).isEmpty();
        }

        @Test
        void createProduct_idempotentReplay() {
            UUID sellerId = persistedSeller(sellers).seller().getId();
            CreateProductCommand command = createProductCommand("Widget", 999, sellerId).withIdempotencyKey("create-key");

            CreateProductCommandResult first = service.createProduct(command);
            CreateProductCommandResult second = service.createProduct(command);

            // The second call must replay the cached result, not create a new product.
            assertThat(products.products).hasSize(1);
            assertThat(second.result().id()).isEqualTo(first.result().id());
            assertThat(second.result().price()).isEqualTo(first.result().price());
        }

        @Test
        void updateProduct_sellerChangedNotFound() {
            UUID sellerId = persistedSeller(sellers).seller().getId();
            UUID productId = createdProductId(sellerId);

            // A different, non-existent seller must fail the seller lookup branch.
            assertThatThrownBy(() -> service.updateProduct(
                    new UpdateProductCommand("", productId, "Widget", 999, Currency.USD, UUID.randomUUID())))
                    .isInstanceOf(SellerNotFoundException.class)
                    .hasMessage("seller not found");
        }

        @Test
        void updateProduct_sellerChangedSuccess() {
            UUID sellerA = persistedSeller(sellers).seller().getId();
            UUID productId = createdProductId(sellerA);

            ValidatedSeller sellerB = ValidatedSeller.of(Seller.create("Globex"));
            sellers.create(sellerB);

            UpdateProductCommandResult updated = service.updateProduct(new UpdateProductCommand(
                    "", productId, "Widget", 999, Currency.USD, sellerB.seller().getId()));

            assertThat(updated.result().sellerId()).isEqualTo(sellerB.seller().getId());
        }

        @Test
        void updateProduct_idempotentReplay() {
            UUID sellerId = persistedSeller(sellers).seller().getId();
            UUID productId = createdProductId(sellerId);
            UpdateProductCommand command =
                    new UpdateProductCommand("upd-key", productId, "Widget v2", 1999, Currency.USD, sellerId);

            UpdateProductCommandResult first = service.updateProduct(command);
            UpdateProductCommandResult second = service.updateProduct(command);

            assertThat(second.result().name()).isEqualTo(first.result().name());
            assertThat(second.result().price()).isEqualTo(first.result().price());
        }

        @Test
        void deleteProduct_idempotentReplay() {
            UUID sellerId = persistedSeller(sellers).seller().getId();
            UUID productId = createdProductId(sellerId);
            DeleteProductCommand command = new DeleteProductCommand("del-key", productId);

            DeleteProductCommandResult first = service.deleteProduct(command);
            // The product is gone, but the replay returns the cached success
            // instead of re-running (and failing with "product not found").
            DeleteProductCommandResult second = service.deleteProduct(command);

            assertThat(first.success()).isTrue();
            assertThat(second.success()).isTrue();
        }
    }

    @Nested
    class SellerServiceErrorPaths {

        private final FakeSellerRepository sellers = new FakeSellerRepository();
        private final DefaultSellerService service = sellerService(sellers);

        @Test
        void updateSeller_notFound() {
            assertThatThrownBy(() -> service.updateSeller(new UpdateSellerCommand("", UUID.randomUUID(), "Acme")))
                    .isInstanceOf(SellerNotFoundException.class)
                    .hasMessage("seller not found");
        }

        @Test
        void updateSeller_validationError() {
            UUID sellerId = service.createSeller(createSellerCommand("Acme")).result().id();

            assertThatThrownBy(() -> service.updateSeller(new UpdateSellerCommand("", sellerId, "")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name must not be empty");
        }

        @Test
        void deleteSeller_notFound() {
            assertThatThrownBy(() -> service.deleteSeller(new DeleteSellerCommand("", UUID.randomUUID())))
                    .isInstanceOf(SellerNotFoundException.class)
                    .hasMessage("seller not found");
        }

        @Test
        void deleteSeller_success() {
            UUID sellerId = service.createSeller(createSellerCommand("Acme")).result().id();

            DeleteSellerCommandResult result = service.deleteSeller(new DeleteSellerCommand("", sellerId));

            assertThat(result.success()).isTrue();
            assertThat(sellers.sellers).isEmpty();
        }

        @Test
        void createSeller_idempotentReplay() {
            CreateSellerCommand command = createSellerCommand("Acme").withIdempotencyKey("seller-key");

            CreateSellerCommandResult first = service.createSeller(command);
            CreateSellerCommandResult second = service.createSeller(command);

            assertThat(sellers.sellers).hasSize(1);
            assertThat(second.result().id()).isEqualTo(first.result().id());
        }

        @Test
        void updateSeller_idempotentReplay() {
            UUID sellerId = service.createSeller(createSellerCommand("Acme")).result().id();
            UpdateSellerCommand command = new UpdateSellerCommand("upd-seller", sellerId, "Acme v2");

            UpdateSellerCommandResult first = service.updateSeller(command);
            UpdateSellerCommandResult second = service.updateSeller(command);

            assertThat(second.result().name()).isEqualTo(first.result().name());
        }

        @Test
        void deleteSeller_idempotentReplay() {
            UUID sellerId = service.createSeller(createSellerCommand("Acme")).result().id();
            DeleteSellerCommand command = new DeleteSellerCommand("del-seller", sellerId);

            DeleteSellerCommandResult first = service.deleteSeller(command);
            DeleteSellerCommandResult second = service.deleteSeller(command);

            assertThat(first.success()).isTrue();
            assertThat(second.success()).isTrue();
        }

        /** A seller lookup that finds nothing is an empty result, not an error. */
        @Test
        void findSellerById_notFound() {
            assertThat(service.findSellerById(new GetSellerByIdQuery(UUID.randomUUID()))).isEmpty();
        }
    }
}
