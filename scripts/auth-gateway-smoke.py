#!/usr/bin/env python3
"""Non-destructive live contract checks. Never create accounts or send email/SMS."""
import json
import unittest
import urllib.error
import urllib.request

BASE = "https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/saba-auth-gateway"

def post(path, body):
    request = urllib.request.Request(BASE + path, data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)

class GatewaySmoke(unittest.TestCase):
    def test_invalid_registration_is_rejected_without_email(self):
        status, body = post("/v1/auth/registration/start", {"email": "", "password": ""})
        self.assertEqual(400, status)
        self.assertNotIn("accessToken", body)

    def test_invalid_login_identifier_is_rejected(self):
        status, body = post("/v1/auth/login/password", {"identifier": "", "password": ""})
        self.assertEqual(400, status)
        self.assertNotIn("accessToken", body)

    def test_logout_without_access_token_is_rejected(self):
        status, body = post("/v1/auth/logout", {})
        self.assertEqual(400, status)
        self.assertFalse(body.get("success", False))

if __name__ == "__main__":
    unittest.main(verbosity=2)
