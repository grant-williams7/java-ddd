package com.example.marketplace.application.services;

import com.example.marketplace.application.command.CreateSellerCommand;
import com.example.marketplace.application.command.CreateSellerCommandResult;
import com.example.marketplace.application.command.DeleteSellerCommand;
import com.example.marketplace.application.command.DeleteSellerCommandResult;
import com.example.marketplace.application.command.UpdateSellerCommand;
import com.example.marketplace.application.command.UpdateSellerCommandResult;
import com.example.marketplace.application.interfaces.SellerService;
import com.example.marketplace.application.query.GetAllSellersQueryResult;
import com.example.marketplace.application.query.GetSellerByIdQuery;
import com.example.marketplace.application.query.GetSellerByIdQueryResult;
import com.example.marketplace.domain.entities.Seller;
import com.example.marketplace.domain.entities.SellerNotFoundException;
import com.example.marketplace.domain.entities.ValidatedSeller;
import com.example.marketplace.domain.repositories.SellerRepository;
import java.util.Optional;

final class DefaultSellerService implements SellerService {

    private final SellerRepository repository;
    private final Idempotency idempotency;

    DefaultSellerService(SellerRepository repository, Idempotency idempotency) {
        this.repository = repository;
        this.idempotency = idempotency;
    }

    @Override
    public CreateSellerCommandResult createSeller(CreateSellerCommand command) {
        return idempotency.withIdempotency(command.idempotencyKey(), command, CreateSellerCommandResult.class, () -> {
            ValidatedSeller seller = ValidatedSeller.of(Seller.create(command.name()));
            repository.create(seller);

            return new CreateSellerCommandResult(SellerResultMapper.fromValidatedEntity(seller));
        });
    }

    @Override
    public GetAllSellersQueryResult findAllSellers() {
        return new GetAllSellersQueryResult(repository.findAll().stream()
                .map(SellerResultMapper::fromEntity)
                .toList());
    }

    @Override
    public Optional<GetSellerByIdQueryResult> findSellerById(GetSellerByIdQuery query) {
        return repository.findById(query.id())
                .map(seller -> new GetSellerByIdQueryResult(SellerResultMapper.fromEntity(seller)));
    }

    @Override
    public UpdateSellerCommandResult updateSeller(UpdateSellerCommand command) {
        return idempotency.withIdempotency(command.idempotencyKey(), command, UpdateSellerCommandResult.class, () -> {
            Seller existing = repository.findById(command.id()).orElseThrow(SellerNotFoundException::new);
            existing.updateName(command.name());

            ValidatedSeller seller = ValidatedSeller.of(existing);
            repository.update(seller);

            return new UpdateSellerCommandResult(SellerResultMapper.fromValidatedEntity(seller));
        });
    }

    @Override
    public DeleteSellerCommandResult deleteSeller(DeleteSellerCommand command) {
        return idempotency.withIdempotency(command.idempotencyKey(), command, DeleteSellerCommandResult.class, () -> {
            repository.findById(command.id()).orElseThrow(SellerNotFoundException::new);
            repository.delete(command.id());

            return new DeleteSellerCommandResult(true);
        });
    }
}
