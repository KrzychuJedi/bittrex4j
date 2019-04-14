/*
 * *
 *  This file is part of the bittrex4j project.
 *
 *  @author CCob
 *
 *  For the full copyright and license information, please view the LICENSE
 *  file that was distributed with this source code.
 * /
 */

package com.github.ccob.bittrex4j.listeners;

import com.github.ccob.bittrex4j.dao.SimpleExchangeSummaryState;

public interface UpdateSimpleSummaryStateListener extends Listener<SimpleExchangeSummaryState> {
    void onEvent(SimpleExchangeSummaryState exchangeSummaryState);
}
