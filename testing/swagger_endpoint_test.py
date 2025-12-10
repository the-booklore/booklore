#!/usr/bin/env python3
"""
Credits: this was heavily inspired by Stirling-PDF apporach
This script tests all API endpoints documented in the OpenAPI specification.
It fetches the API specification from the running server and attempts to
call each endpoint with appropriate test data.
Usage:
    python swagger_endpoint_test.py [--base-url URL] [--username USER] [--password PASS]
                                    [--enable-auth] [--verbose] [--skip-setup]
                                    [--skip-destructive] [--openapi-url URL]
"""

import argparse
import json
import logging
import os
import sys
import time
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Set, Tuple
from urllib.parse import urljoin

import requests


logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)



@dataclass
class TestConfig:
    base_url: str = "http://localhost:6060"
    openapi_url: str = "/api/v1/api-docs"
    username: str = "admin"
    password: str = "admin"
    enable_auth: bool = False
    verbose: bool = False
    skip_setup: bool = False
    skip_destructive: bool = True
    timeout: int = 30
    retry_count: int = 3
    retry_delay: float = 2.0


@dataclass
class EndpointResult:
    path: str
    method: str
    status_code: int
    success: bool
    response_time: float
    error: Optional[str] = None
    skipped: bool = False
    skip_reason: Optional[str] = None


@dataclass
class TestSummary:
    total: int = 0
    passed: int = 0
    failed: int = 0
    skipped: int = 0
    auth_required: int = 0
    results: List[EndpointResult] = field(default_factory=list)

class TestDataGenerator:

    DEFAULTS = {
        "integer": 1,
        "number": 1.0,
        "string": "test",
        "boolean": True,
        "array": [],
        "object": {},
    }
    
    BOOKLORE_PARAMS = {
        "bookId": 1,
        "libraryId": 1,
        "userId": 1,
        "shelfId": 1,
        "taskId": 1,
        "noteId": 1,
        "reviewId": 1,
        "authorId": 1,
        "page": 0,
        "size": 10,
        "ids": [1],
        "withDescription": False,
        "includeBooks": False,
        "libraryIds": [1],
        "username": "testuser",
        "email": "test@example.com",
        "name": "Test",
    }
    
    @classmethod
    def get_value_for_param(cls, name: str, schema: Dict[str, Any]) -> Any:
        if name in cls.BOOKLORE_PARAMS:
            return cls.BOOKLORE_PARAMS[name]
        
        param_type = schema.get("type", "string")
        param_format = schema.get("format", "")
        
        if "enum" in schema:
            return schema["enum"][0]
        
        if param_type == "array":
            items = schema.get("items", {})
            item_type = items.get("type", "string")
            if item_type == "integer":
                return [1]
            return ["test"]
        
        if param_format == "int64":
            return 1
        if param_format == "int32":
            return 1
        if param_format == "date-time":
            return "2024-01-01T00:00:00Z"
        if param_format == "date":
            return "2024-01-01"
        if param_format == "email":
            return "test@example.com"
        if param_format == "uri":
            return "https://example.com"
        if param_format == "uuid":
            return "00000000-0000-0000-0000-000000000001"
        
        return cls.DEFAULTS.get(param_type, "test")
    
    @classmethod
    def generate_request_body(cls, schema: Dict[str, Any], 
                             definitions: Dict[str, Any] = None) -> Dict[str, Any]:
        if not schema:
            return {}
        
        if "$ref" in schema:
            ref_path = schema["$ref"]
            ref_name = ref_path.split("/")[-1]
            if definitions and ref_name in definitions:
                return cls.generate_request_body(definitions[ref_name], definitions)
            return {}
        
        if "allOf" in schema:
            result = {}
            for sub_schema in schema["allOf"]:
                result.update(cls.generate_request_body(sub_schema, definitions))
            return result
        
        if "oneOf" in schema or "anyOf" in schema:
            schemas = schema.get("oneOf") or schema.get("anyOf")
            if schemas:
                return cls.generate_request_body(schemas[0], definitions)
            return {}
        
        if schema.get("type") == "object" or "properties" in schema:
            result = {}
            properties = schema.get("properties", {})
            required = schema.get("required", [])
            
            for prop_name, prop_schema in properties.items():
                if prop_name in required or prop_name in cls.BOOKLORE_PARAMS:
                    result[prop_name] = cls.get_value_for_param(prop_name, prop_schema)
            
            return result
        
        if schema.get("type") == "array":
            items_schema = schema.get("items", {})
            return [cls.generate_request_body(items_schema, definitions)]
        
        # Handle primitive types
        return cls.get_value_for_param("", schema)

class EndpointClassifier:

    AUTH_REQUIRED_PATTERNS = [
        "/api/v1/books",
        "/api/v1/libraries",
        "/api/v1/shelves",
        "/api/v1/users",
        "/api/v1/settings",
        "/api/v1/tasks",
        "/api/v1/metadata",
        "/api/v1/authors",
        "/api/v1/notes",
        "/api/v1/reviews",
        "/api/v1/kobo",
        "/api/v1/koreader",
        "/api/v1/opds",
        "/api/v1/email",
    ]
    
    PUBLIC_ENDPOINTS = [
        "/api/v1/version",
        "/api/v1/version/changelog",
        "/api/v1/public-settings",
        "/api/v1/setup/status",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/remote",
        "/api/v1/actuator/health",
    ]
    
    DESTRUCTIVE_PATTERNS = [
        ("DELETE", "/api/v1/books"),
        ("DELETE", "/api/v1/libraries"),
        ("DELETE", "/api/v1/shelves"),
        ("DELETE", "/api/v1/users"),
        ("DELETE", "/api/v1/tasks"),
        ("POST", "/api/v1/setup"),  # Initial setup - one time only
        ("POST", "/api/v1/auth/register"),  # User creation
    ]
    
    SKIP_ENDPOINTS = [
        "/api/v1/upload",
        "/api/v1/background-upload",
        "/api/v1/bookdrop-files",
        "/api/v1/files/move",
        "/content",
        "/cover",
        "/thumbnail",
        "/image",
        "/download",
        "/api/v1/opds",
        # Reader endpoints (require book content)
        "/api/v1/cbx-reader",
        "/api/v1/pdf-reader",
        # Kobo sync (complex state machine)
        "/api/v1/kobo",
        # Koreader sync
        "/api/v1/koreader",
        # Additional files
        "/api/v1/additional-files",
        # Remote auth endpoint - returns 203 (temporarily skip)
        "/api/v1/auth/remote",
    ]
    
    SPECIAL_ENDPOINTS = {
        "POST /api/v1/auth/login": {
            "username": "admin",
            "password": "admin",
        },
        "POST /api/v1/auth/refresh": {
            "refreshToken": "dummy-token",
        },
    }
    
    @classmethod
    def requires_auth(cls, path: str) -> bool:
        for public_path in cls.PUBLIC_ENDPOINTS:
            if path == public_path or path.startswith(public_path):
                return False
        
        for pattern in cls.AUTH_REQUIRED_PATTERNS:
            if path.startswith(pattern):
                return True
        
        return False
    
    @classmethod
    def should_skip(cls, path: str, method: str) -> Tuple[bool, Optional[str]]:
        for skip_pattern in cls.SKIP_ENDPOINTS:
            if skip_pattern in path:
                return True, f"Matches skip pattern: {skip_pattern}"
        
        return False, None
    
    @classmethod
    def is_destructive(cls, method: str, path: str) -> bool:
        for destructive_method, destructive_pattern in cls.DESTRUCTIVE_PATTERNS:
            if method.upper() == destructive_method and path.startswith(destructive_pattern):
                return True
        return False
    
    @classmethod
    def get_special_body(cls, method: str, path: str) -> Optional[Dict[str, Any]]:
        key = f"{method.upper()} {path}"
        return cls.SPECIAL_ENDPOINTS.get(key)


class BookLoreApiClient:

    def __init__(self, config: TestConfig):
        self.config = config
        self.session = requests.Session()
        self.session.headers.update({
            "Content-Type": "application/json",
            "Accept": "application/json",
        })
        self.access_token: Optional[str] = None
    
    def login(self) -> bool:
        url = urljoin(self.config.base_url, "/api/v1/auth/login")
        payload = {
            "username": self.config.username,
            "password": self.config.password,
        }
        
        try:
            response = self.session.post(
                url, 
                json=payload, 
                timeout=self.config.timeout
            )
            
            if response.status_code == 200:
                data = response.json()
                self.access_token = data.get("accessToken") or data.get("token")
                if self.access_token:
                    self.session.headers.update({
                        "Authorization": f"Bearer {self.access_token}"
                    })
                    logger.info("Successfully authenticated")
                    return True
            
            logger.warning(f"Authentication failed: {response.status_code}")
            return False
            
        except Exception as e:
            logger.error(f"Authentication error: {e}")
            return False
    
    def fetch_openapi_spec(self) -> Optional[Dict[str, Any]]:
        url = urljoin(self.config.base_url, self.config.openapi_url)
        
        for attempt in range(self.config.retry_count):
            try:
                response = self.session.get(url, timeout=self.config.timeout)
                
                if response.status_code == 200:
                    return response.json()
                
                logger.warning(f"Failed to fetch OpenAPI spec: {response.status_code}")
                
            except requests.exceptions.ConnectionError:
                logger.warning(f"Connection error (attempt {attempt + 1}/{self.config.retry_count})")
                time.sleep(self.config.retry_delay)
            except Exception as e:
                logger.error(f"Error fetching OpenAPI spec: {e}")
                break
        
        return None
    
    def call_endpoint(self, method: str, path: str, 
                     params: Dict[str, Any] = None,
                     body: Dict[str, Any] = None,
                     headers: Dict[str, str] = None) -> Tuple[int, float, Optional[str]]:
        url = urljoin(self.config.base_url, path)
        
        try:
            start_time = time.time()
            
            request_kwargs = {
                "timeout": self.config.timeout,
                "params": params,
            }
            
            if body is not None:
                request_kwargs["json"] = body
            
            if headers:
                request_kwargs["headers"] = {**self.session.headers, **headers}
            
            response = self.session.request(method, url, **request_kwargs)
            
            response_time = time.time() - start_time
            
            return response.status_code, response_time, None
            
        except requests.exceptions.Timeout:
            return 0, 0, "Request timeout"
        except requests.exceptions.ConnectionError:
            return 0, 0, "Connection error"
        except Exception as e:
            return 0, 0, str(e)


class SwaggerEndpointTester:

    SUCCESS_CODES = {
        "GET": {200, 204, 304},
        "POST": {200, 201, 202, 204},
        "PUT": {200, 201, 204},
        "PATCH": {200, 204},
        "DELETE": {200, 202, 204},
    }
    
    ACCEPTABLE_CODES = {
        400,  # Bad request (expected for minimal test data)
        401,  # Unauthorized (expected without auth)
        403,  # Forbidden (expected without proper permissions)
        404,  # Not found (expected for non-existent IDs)
        405,  # Method not allowed
        409,  # Conflict (expected for duplicate resources)
        422,  # Unprocessable entity (validation error)
    }
    
    def __init__(self, config: TestConfig):
        self.config = config
        self.client = BookLoreApiClient(config)
        self.summary = TestSummary()
        self.openapi_spec: Optional[Dict[str, Any]] = None
    
    def setup(self) -> bool:
        logger.info(f"Connecting to {self.config.base_url}")
        
        self.openapi_spec = self.client.fetch_openapi_spec()
        if not self.openapi_spec:
            logger.error("Failed to fetch OpenAPI specification")
            logger.info("Make sure Swagger is enabled: SWAGGER_ENABLED=true")
            return False
        
        logger.info(f"OpenAPI spec version: {self.openapi_spec.get('openapi', 'unknown')}")
        if self.config.enable_auth:
            if not self.client.login():
                logger.warning("Authentication failed - some tests may fail")
        
        return True
    
    def get_endpoints(self) -> List[Tuple[str, str, Dict[str, Any]]]:
        endpoints = []
        
        paths = self.openapi_spec.get("paths", {})
        for path, path_item in paths.items():
            for method in ["get", "post", "put", "patch", "delete"]:
                if method in path_item:
                    endpoints.append((method.upper(), path, path_item[method]))
        
        return endpoints
    
    def prepare_request(self, method: str, path: str, 
                       operation: Dict[str, Any]) -> Tuple[str, Dict, Dict]:
        resolved_path = path
        query_params = {}
        request_body = {}
        
        schemas = self.openapi_spec.get("components", {}).get("schemas", {})
        
        parameters = operation.get("parameters", [])
        for param in parameters:
            param_name = param.get("name")
            param_in = param.get("in")
            param_schema = param.get("schema", {})
            required = param.get("required", False)
            
            if not required and param_name not in TestDataGenerator.BOOKLORE_PARAMS:
                continue
            
            value = TestDataGenerator.get_value_for_param(param_name, param_schema)
            
            if param_in == "path":
                resolved_path = resolved_path.replace(f"{{{param_name}}}", str(value))
            elif param_in == "query":
                query_params[param_name] = value
        
        request_body_spec = operation.get("requestBody", {})
        if request_body_spec:
            content = request_body_spec.get("content", {})
            json_content = content.get("application/json", {})
            body_schema = json_content.get("schema", {})
            
            special_body = EndpointClassifier.get_special_body(method, path)
            if special_body:
                request_body = special_body
            else:
                request_body = TestDataGenerator.generate_request_body(body_schema, schemas)
        
        return resolved_path, query_params, request_body
    
    def _check_skip_conditions(self, method: str, path: str) -> Optional[str]:
        should_skip, skip_reason = EndpointClassifier.should_skip(path, method)
        if should_skip:
            return skip_reason
        
        if self.config.skip_destructive and EndpointClassifier.is_destructive(method, path):
            return "Destructive endpoint skipped"
        
        requires_auth = EndpointClassifier.requires_auth(path)
        if requires_auth and not self.config.enable_auth:
            return "Requires authentication (use --enable-auth)"
        
        return None
    
    def _determine_success(self, error: Optional[str], status_code: int, method: str) -> bool:
        if error:
            return False
        if status_code in self.SUCCESS_CODES.get(method, set()):
            return True
        if status_code in self.ACCEPTABLE_CODES:
            return True
        return status_code != 500
    
    def test_endpoint(self, method: str, path: str, 
                     operation: Dict[str, Any]) -> EndpointResult:
        skip_reason = self._check_skip_conditions(method, path)
        if skip_reason:
            return EndpointResult(
                path=path,
                method=method,
                status_code=0,
                success=True,
                response_time=0,
                skipped=True,
                skip_reason=skip_reason
            )
        
        resolved_path, query_params, request_body = self.prepare_request(
            method, path, operation
        )
        
        status_code, response_time, error = self.client.call_endpoint(
            method, resolved_path, query_params, 
            request_body if request_body else None
        )
        
        success = self._determine_success(error, status_code, method)
        if status_code == 500 and not error:
            error = "Internal Server Error"
        
        if self.config.verbose:
            status_str = "✓" if success else "✗"
            logger.info(f"{status_str} {method} {resolved_path} -> {status_code} ({response_time:.2f}s)")
        
        return EndpointResult(
            path=path,
            method=method,
            status_code=status_code,
            success=success,
            response_time=response_time,
            error=error
        )
    
    def run(self) -> TestSummary:
        if not self.setup():
            return self.summary
        
        endpoints = self.get_endpoints()
        logger.info(f"Found {len(endpoints)} endpoints to test")
        
        for method, path, operation in endpoints:
            result = self.test_endpoint(method, path, operation)
            self.summary.results.append(result)
            self.summary.total += 1
            
            if result.skipped:
                self.summary.skipped += 1
            elif result.success:
                self.summary.passed += 1
            else:
                self.summary.failed += 1
        
        return self.summary
    
    def print_summary(self):
        print("\n" + "=" * 60)
        print("BOOKLORE API ENDPOINT TEST SUMMARY")
        print("=" * 60)
        print(f"Total endpoints: {self.summary.total}")
        print(f"Passed: {self.summary.passed}")
        print(f"Failed: {self.summary.failed}")
        print(f"Skipped: {self.summary.skipped}")
        print("=" * 60)
        
        failures = [r for r in self.summary.results if not r.success and not r.skipped]
        if failures:
            print("\nFAILED ENDPOINTS:")
            for result in failures:
                error_msg = result.error or f"HTTP {result.status_code}"
                print(f"  ✗ {result.method} {result.path}: {error_msg}")
        
        if self.config.verbose:
            skipped = [r for r in self.summary.results if r.skipped]
            if skipped:
                print("\nSKIPPED ENDPOINTS:")
                for result in skipped:
                    print(f"  - {result.method} {result.path}: {result.skip_reason}")
        
        print()
        
        success_rate = (self.summary.passed / max(self.summary.total - self.summary.skipped, 1)) * 100
        print(f"Success rate: {success_rate:.1f}%")
        
        return self.summary.failed == 0


def parse_args() -> TestConfig:
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(
        description="Test BookLore API endpoints using OpenAPI specification"
    )
    
    parser.add_argument(
        "--base-url", 
        default=os.environ.get("BOOKLORE_URL", "http://localhost:6060"),
        help="Base URL of the BookLore server (default: http://localhost:6060)"
    )
    parser.add_argument(
        "--openapi-url",
        default="/api/v1/api-docs",
        help="Path to OpenAPI spec (default: /api/v1/api-docs)"
    )
    parser.add_argument(
        "--username",
        default=os.environ.get("BOOKLORE_USER", "admin"),
        help="Username for authentication"
    )
    parser.add_argument(
        "--password",
        default=os.environ.get("BOOKLORE_PASSWORD", "admin"),
        help="Password for authentication"
    )
    parser.add_argument(
        "--enable-auth",
        action="store_true",
        help="Enable authentication for protected endpoints"
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Enable verbose output"
    )
    parser.add_argument(
        "--skip-setup",
        action="store_true",
        help="Skip setup endpoints"
    )
    parser.add_argument(
        "--skip-destructive",
        action="store_true",
        default=True,
        help="Skip destructive endpoints (DELETE, etc.)"
    )
    parser.add_argument(
        "--include-destructive",
        action="store_true",
        help="Include destructive endpoints (overrides --skip-destructive)"
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=30,
        help="Request timeout in seconds (default: 30)"
    )
    
    args = parser.parse_args()
    
    return TestConfig(
        base_url=args.base_url,
        openapi_url=args.openapi_url,
        username=args.username,
        password=args.password,
        enable_auth=args.enable_auth,
        verbose=args.verbose,
        skip_setup=args.skip_setup,
        skip_destructive=not args.include_destructive,
        timeout=args.timeout,
    )


def main():
    """Main entry point."""
    config = parse_args()
    
    if config.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    logger.info("Starting BookLore API Endpoint Tests")
    logger.info(f"Target: {config.base_url}")
    logger.info(f"Auth enabled: {config.enable_auth}")
    
    tester = SwaggerEndpointTester(config)
    tester.run()
    
    success = tester.print_summary()
    
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
