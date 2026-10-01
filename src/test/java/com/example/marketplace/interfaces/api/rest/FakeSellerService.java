package com.example.marketplace.interfaces.api.rest;

import com.example.marketplace.application.command.CreateSellerCommand;
import com.example.marketplace.application.command.CreateSellerCommandResult;
import com.example.marketplace.application.command.DeleteSellerCommand;
import com.example.marketplace.application.command.DeleteSellerCommandResult;
import com.example.marketplace.application.command.UpdateSellerCommand;
import com.example.marketplace.application.command.UpdateSellerCommandResult;
import com.example.marketplace.application.common.SellerResult;
import com.example.marketplace.application.interfaces.SellerService;
import com.example.marketplace.application.query.GetAllSellersQueryResult;
import com.example.marketplace.application.query.GetSellerByIdQuery;
import com.example.marketplace.application.query.GetSellerByIdQueryResult;
import com.example.marketplace.domain.entities.Seller;
import com.example.marketplace.domain.entities.SellerNotFoundException;
import com.example.marketplace.domain.entities.ValidatedSeller;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** An in-memory seller service for controller tests. */
class FakeSellerService implements SellerService {

    private final Map<UUID, SellerResult> sellers = new LinkedHashMap<>();

    @Override
    public CreateSellerCommandResult createSeller(CreateSellerCommand command) {
        Seller seller = ValidatedSeller.of(Seller.create(command.name())).seller();
        SellerResult result = new SellerResult(seller.getId(), seller.getName(), seller.getCreatedAt(),
                seller.getUpdatedAt());
        sellers.put(result.id(), result);
        return new CreateSellerCommandResult(result);
    }

    @Override
    public GetAllSellersQueryResult findAllSellers() {
        return new GetAllSellersQueryResult(new ArrayList<>(sellers.values()));
    }

    @Override
    public Optional<GetSellerByIdQueryResult> findSellerById(GetSellerByIdQuery query) {
        return Optional.ofNullable(sellers.get(query.id())).map(GetSellerByIdQueryResult::new);
    }

    @Override
    public UpdateSellerCommandResult updateSeller(UpdateSellerCommand command) {
        SellerResult existing = sellers.get(command.id());
        if (existing == null) {
            throw new SellerNotFoundException();
        }
        SellerResult updated = new SellerResult(existing.id(), command.name(), existing.createdAt(),
                existing.updatedAt());
        sellers.put(updated.id(), updated);
        return new UpdateSellerCommandResult(updated);
    }

    @Override
    public DeleteSellerCommandResult deleteSeller(DeleteSellerCommand command) {
        if (sellers.remove(command.id()) == null) {
            throw new SellerNotFoundException();
        }
        return new DeleteSellerCommandResult(true);
    }
}
