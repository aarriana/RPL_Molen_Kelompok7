package com.molen.rpl.map.presenter;

import android.content.Context;

/**
 * Created by Aman on 19/07/17.
 */

public interface IInvitePresenter<V>  extends Presenter<V>{
    void acceptInvite(String userID, String accountID, Context context);
}
