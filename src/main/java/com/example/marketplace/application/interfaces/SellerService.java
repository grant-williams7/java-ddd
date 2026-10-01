package com.example.marketplace.application.interfaces;

import com.example.marketplace.application.command.CreateSellerCommand;
import com.example.marketplace.application.command.CreateSellerCommandResult;
import com.example.marketplace.application.command.DeleteSellerCommand;
import com.example.marketplace.application.command.DeleteSellerCommandResult;
import com.example.marketplace.application.command.UpdateSellerCommand;
import com.example.marketplace.application.command.UpdateSellerCommandResult;
import com.example.marketplace.application.query.GetAllSellersQueryResult;
import com.example.marketplace.application.query.GetSellerByIdQuery;
import com.example.marketplace.application.query.GetSellerByIdQueryResult;
import java.util.Optional;

public interface SellerService {

    CreateSellerCommandResult createSeller(CreateSellerCommand command);

    GetAllSellersQueryResult findAllSellers();

    /** Empty when the seller doesn't exist; the caller decides what that means (a 404). */
    Optional<GetSellerByIdQueryResult> findSellerById(GetSellerByIdQuery query);

    UpdateSellerCommandResult updateSeller(UpdateSellerCommand command);

    DeleteSellerCommandResult deleteSeller(DeleteSellerCommand command);
}
