package com.example.marketplace.application.interfaces;

import com.example.marketplace.application.command.CreateProductCommand;
import com.example.marketplace.application.command.CreateProductCommandResult;
import com.example.marketplace.application.command.DeleteProductCommand;
import com.example.marketplace.application.command.DeleteProductCommandResult;
import com.example.marketplace.application.command.UpdateProductCommand;
import com.example.marketplace.application.command.UpdateProductCommandResult;
import com.example.marketplace.application.query.GetAllProductsQueryResult;
import com.example.marketplace.application.query.GetProductByIdQuery;
import com.example.marketplace.application.query.GetProductByIdQueryResult;
import java.util.Optional;

public interface ProductService {

    CreateProductCommandResult createProduct(CreateProductCommand command);

    UpdateProductCommandResult updateProduct(UpdateProductCommand command);

    DeleteProductCommandResult deleteProduct(DeleteProductCommand command);

    GetAllProductsQueryResult findAllProducts();

    /** Empty when the product doesn't exist; the caller decides what that means (a 404). */
    Optional<GetProductByIdQueryResult> findProductById(GetProductByIdQuery query);
}
