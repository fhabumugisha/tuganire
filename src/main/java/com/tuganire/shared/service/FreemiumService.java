package com.tuganire.shared.service;

import com.tuganire.auth.model.User;
import com.tuganire.shared.constant.PlanType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FreemiumService {

    @Value("${tuganire.freemium.max-items:40}")
    private int maxItems;

    public boolean canCreateItem(User user, long currentCount) {
        if (user.getPlan() == PlanType.PREMIUM) {
            return true;
        }

        return currentCount < maxItems;
    }

    public int getMaxItems() {
        return maxItems;
    }

    /**
     * @deprecated Use getMaxItems() instead
     */
    @Deprecated
    public int getMaxSermons() {
        return maxItems;
    }
}
