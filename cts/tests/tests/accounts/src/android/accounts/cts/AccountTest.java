package android.accounts.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.accounts.Account;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AccountTest {

    private Account account;

    @Before
    public void setUp() throws Exception {
        account = new Account("abc@xyz.org", "com.my.auth");
    }

    @Test
    public void testAccountObjectCreationWithNullName() {
        try {
            new Account((String) null, "com.my.auth");
            fail();
        } catch (IllegalArgumentException expectedException) {
        }
    }

    @Test
    public void testAccountObjectCreationWithNullAccountType() {
        try {
            new Account("abc@xyz.org", (String) null);
            fail();
        } catch (IllegalArgumentException expectedException) {
        }
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, account.describeContents());
    }

    @Test
    public void testWriteToParcel() {
        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        account.writeToParcel(parcel, 0);
        // Reset the position to initial.
        parcel.setDataPosition(0);
        // Create a new account object from just populated parcel,
        // and verify it is equivalent to the original account.
        Account newAccount = new Account(parcel);
        assertEquals(account, newAccount);

        parcel.recycle();
    }

}
