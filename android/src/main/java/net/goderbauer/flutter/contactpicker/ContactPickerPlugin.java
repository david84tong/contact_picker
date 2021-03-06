// Copyright 2017 Michael Goderbauer. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package net.goderbauer.flutter.contactpicker;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.HashMap;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import static android.app.Activity.RESULT_OK;

public class ContactPickerPlugin implements MethodCallHandler, PluginRegistry.ActivityResultListener {
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "contact_picker");
    ContactPickerPlugin instance = new ContactPickerPlugin(registrar.activity());
    registrar.addActivityResultListener(instance);
    channel.setMethodCallHandler(instance);
  }

  private ContactPickerPlugin(Activity activity) {
    this.activity = activity;
  }

  private static int PICK_CONTACT = 2015;

  private Activity activity;
  private Result pendingResult;

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    if (call.method.equals("selectContact")) {
      if (pendingResult != null) {
        pendingResult.error("multiple_requests", "Cancelled by a second request.", null);
        pendingResult = null;
      }
      pendingResult = result;

      Intent i = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Email.CONTENT_URI);
      activity.startActivityForResult(i, PICK_CONTACT);
    } else {
      result.notImplemented();
    }
  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode != PICK_CONTACT) {
      return false;
    }
    if (resultCode != RESULT_OK) {
      pendingResult.success(null);
      pendingResult = null;
      return true;
    }
    HashMap<String, Object> contact = new HashMap<>();
    Uri contactUri = data.getData();
    Cursor cursor = activity.getContentResolver().query(contactUri, null, null, null, null);
    cursor.moveToFirst();

    int emailType = cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE));
    String customLabel = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.LABEL));
    String label = (String) ContactsContract.CommonDataKinds.Email.getTypeLabel(activity.getResources(), emailType, customLabel);
    String email = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA));
    String fullName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
    String[] separatedFullName = fullName.split(" ");
    if(separatedFullName.length > 1) {
      StringBuilder familyNameBuilder = new StringBuilder();
      for (int i = 1; i < separatedFullName.length; i++) {
        familyNameBuilder.append(separatedFullName[i]);
        familyNameBuilder.append(" ");
      }
      contact.put("givenName", separatedFullName[0]);
      contact.put("familyName", familyNameBuilder.toString().trim());
    }

    HashMap<String, Object> emailAddress = new HashMap<>();
    emailAddress.put("email", email);
    emailAddress.put("label", label);
    contact.put("emailAddress", emailAddress);

    pendingResult.success(contact);
    pendingResult = null;
    return true;
  }
}
