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
import random
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple
from urllib.parse import urljoin
from concurrent.futures import ThreadPoolExecutor, as_completed
from threading import Lock
from datetime import datetime

import requests
try:
    from jsonschema import validate, ValidationError
except ImportError:
    validate = None
    ValidationError = None


logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class ResponseValidator:
    """Validate API responses against OpenAPI schema"""
    
    def __init__(self, openapi_spec: Dict[str, Any]):
        self.spec = openapi_spec
        self.schemas = openapi_spec.get("components", {}).get("schemas", {})
    
    def validate_response(self, operation: Dict, status_code: int, 
                         response_body: Any) -> Tuple[bool, Optional[str]]:
        """Validate response against OpenAPI schema"""
        if not validate or not ValidationError:
            return True, None
        
        responses = operation.get("responses", {})
        response_spec = responses.get(str(status_code)) or responses.get("default")
        
        if not response_spec:
            return True, None
        
        content = response_spec.get("content", {})
        json_schema = content.get("application/json", {}).get("schema", {})
        
        if not json_schema:
            return True, None
        
        try:
            resolved_schema = self._resolve_schema(json_schema)
            validate(instance=response_body, schema=resolved_schema)
            return True, None
        except ValidationError as e:
            return False, f"Schema validation failed: {e.message[:100]}"
        except Exception as e:
            return False, f"Validation error: {str(e)[:100]}"
    
    def _resolve_schema(self, schema: Dict) -> Dict:
        """Resolve $ref in schemas"""
        if "$ref" in schema:
            ref_path = schema["$ref"].split("/")
            return self.schemas.get(ref_path[-1], {})
        return schema


class ReportGenerator:
    """Generate test reports in various formats"""
    
    @staticmethod
    def generate_json_report(summary: 'TestSummary', config: TestConfig, 
                            output_path: str = "test_report.json") -> str:
        """Generate JSON report"""
        report = {
            "timestamp": datetime.now().isoformat(),
            "config": {
                "base_url": config.base_url,
                "auth_enabled": config.enable_auth,
            },
            "summary": {
                "total": summary.total,
                "passed": summary.passed,
                "failed": summary.failed,
                "skipped": summary.skipped,
                "success_rate": (summary.passed / max(summary.total - summary.skipped, 1)) * 100
            },
            "results": [
                {
                    "path": r.path,
                    "method": r.method,
                    "status_code": r.status_code,
                    "success": r.success,
                    "response_time": round(r.response_time, 3),
                    "error": r.error,
                    "schema_valid": r.schema_valid,
                    "schema_error": r.schema_error,
                    "skipped": r.skipped,
                    "skip_reason": r.skip_reason
                }
                for r in summary.results
            ]
        }
        
        Path(output_path).write_text(json.dumps(report, indent=2))
        logger.info(f"JSON report generated: {output_path}")
        return output_path
    
    @staticmethod
    def generate_html_report(summary: 'TestSummary', config: TestConfig,
                            output_path: str = "test_report.html") -> str:
        """Generate HTML report"""
        success_rate = (summary.passed / max(summary.total - summary.skipped, 1)) * 100
        
        html = f"""<!DOCTYPE html>
<html>
<head>
    <title>API Test Report</title>
    <style>
        body {{ font-family: Arial, sans-serif; margin: 20px; background: #f9f9f9; }}
        .container {{ max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
        .summary {{ background: #f5f5f5; padding: 20px; border-radius: 5px; margin-bottom: 20px; }}
        .metric {{ display: inline-block; margin-right: 30px; }}
        .metric-label {{ color: #666; font-size: 12px; text-transform: uppercase; }}
        .metric-value {{ font-size: 24px; font-weight: bold; }}
        .success {{ color: #4CAF50; }}
        .failure {{ color: #f44336; }}
        .skipped {{ color: #ff9800; }}
        table {{ width: 100%; border-collapse: collapse; margin-top: 20px; }}
        th, td {{ border: 1px solid #ddd; padding: 12px; text-align: left; }}
        th {{ background-color: #4CAF50; color: white; font-weight: bold; }}
        tr:nth-child(even) {{ background-color: #f2f2f2; }}
        tr:hover {{ background-color: #e8f5e9; }}
        .method-get {{ color: #2196F3; font-weight: bold; }}
        .method-post {{ color: #4CAF50; font-weight: bold; }}
        .method-put {{ color: #FF9800; font-weight: bold; }}
        .method-delete {{ color: #f44336; font-weight: bold; }}
        .method-patch {{ color: #9C27B0; font-weight: bold; }}
        .time-fast {{ color: #4CAF50; }}
        .time-slow {{ color: #f44336; }}
    </style>
</head>
<body>
    <div class="container">
        <h1>📊 BookLore API Test Report</h1>
        <div class="summary">
            <h2>Summary</h2>
            <p><strong>Test Date:</strong> {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
            <p><strong>Base URL:</strong> {config.base_url}</p>
            <div>
                <div class="metric">
                    <div class="metric-label">Total Endpoints</div>
                    <div class="metric-value">{summary.total}</div>
                </div>
                <div class="metric">
                    <div class="metric-label success">Passed</div>
                    <div class="metric-value success">{summary.passed}</div>
                </div>
                <div class="metric">
                    <div class="metric-label failure">Failed</div>
                    <div class="metric-value failure">{summary.failed}</div>
                </div>
                <div class="metric">
                    <div class="metric-label skipped">Skipped</div>
                    <div class="metric-value skipped">{summary.skipped}</div>
                </div>
                <div class="metric">
                    <div class="metric-label">Success Rate</div>
                    <div class="metric-value success">{success_rate:.1f}%</div>
                </div>
            </div>
        </div>
        
        <h2>Test Results</h2>
        <table>
            <tr>
                <th>Method</th>
                <th>Path</th>
                <th>Status</th>
                <th>Time (s)</th>
                <th>Schema</th>
                <th>Result</th>
            </tr>
"""
        
        for result in summary.results:
            method_class = f"method-{result.method.lower()}"
            time_class = "time-fast" if result.response_time < 1.0 else "time-slow"
            
            if result.skipped:
                status_text = "⊘ Skipped"
                status_class = "skipped"
            elif result.success:
                status_text = "✓ Pass"
                status_class = "success"
            else:
                status_text = "✗ Fail"
                status_class = "failure"
            
            schema_text = ""
            if result.schema_valid is not None:
                schema_text = "✓ Valid" if result.schema_valid else "✗ Invalid"
            
            html += f"""            <tr>
                <td><span class="{method_class}">{result.method}</span></td>
                <td><code>{result.path}</code></td>
                <td>{result.status_code}</td>
                <td><span class="{time_class}">{result.response_time:.2f}</span></td>
                <td>{schema_text}</td>
                <td><span class="{status_class}">{status_text}</span></td>
            </tr>
"""
        
        html += """        </table>
    </div>
</body>
</html>
"""
        
        Path(output_path).write_text(html)
        logger.info(f"HTML report generated: {output_path}")
        return output_path



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
    max_workers: int = 10
    output_format: str = "console"
    output_file: str = "test_report"
    validate_response_schema: bool = True


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
    schema_valid: Optional[bool] = None
    schema_error: Optional[str] = None


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
    
    SAMPLE_DATA = {
        "title": ["The Great Gatsby", "1984", "To Kill a Mockingbird", "Pride and Prejudice"],
        "author": ["F. Scott Fitzgerald", "George Orwell", "Harper Lee", "Jane Austen"],
        "isbn": ["978-0-7432-7356-5", "978-0-452-28423-4", "978-0-06-112008-4", "978-0-141-44144-4"],
        "genre": ["Fiction", "Science Fiction", "Classic", "Mystery", "Romance"],
        "language": ["en", "es", "fr", "de", "it"],
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
        name_lower = name.lower()
        
        # Sample data from realistic values
        if "title" in name_lower and "title" in cls.SAMPLE_DATA:
            return random.choice(cls.SAMPLE_DATA["title"])
        if "author" in name_lower and "author" in cls.SAMPLE_DATA:
            return random.choice(cls.SAMPLE_DATA["author"])
        if "isbn" in name_lower and "isbn" in cls.SAMPLE_DATA:
            return random.choice(cls.SAMPLE_DATA["isbn"])
        if "genre" in name_lower and "genre" in cls.SAMPLE_DATA:
            return random.choice(cls.SAMPLE_DATA["genre"])
        if "language" in name_lower and "language" in cls.SAMPLE_DATA:
            return random.choice(cls.SAMPLE_DATA["language"])
        
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
        
        # Number constraints
        if param_type in ["integer", "number"]:
            minimum = schema.get("minimum", 1)
            maximum = schema.get("maximum", 100)
            if param_type == "integer":
                return random.randint(int(minimum), int(maximum))
            return random.uniform(minimum, maximum)
        
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
    
    def call_endpoint_with_response(self, method: str, path: str, 
                                   params: Dict[str, Any] = None,
                                   body: Dict[str, Any] = None,
                                   headers: Dict[str, str] = None) -> Tuple[int, float, Optional[str], Any]:
        """Call endpoint and return response body for validation"""
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
            
            response_body = None
            if response.headers.get('content-type', '').startswith('application/json'):
                try:
                    response_body = response.json()
                except:
                    pass
            
            return response.status_code, response_time, None, response_body
            
        except requests.exceptions.Timeout:
            return 0, 0, "Request timeout", None
        except requests.exceptions.ConnectionError:
            return 0, 0, "Connection error", None
        except Exception as e:
            return 0, 0, str(e), None


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
        self.validator: Optional[ResponseValidator] = None
        self.lock = Lock()  # For thread-safe summary updates
    
    def setup(self) -> bool:
        logger.info(f"Connecting to {self.config.base_url}")
        
        self.openapi_spec = self.client.fetch_openapi_spec()
        if not self.openapi_spec:
            logger.error("Failed to fetch OpenAPI specification")
            logger.info("Make sure Swagger is enabled: SWAGGER_ENABLED=true")
            return False
        
        logger.info(f"OpenAPI spec version: {self.openapi_spec.get('openapi', 'unknown')}")
        
        # Initialize response validator if schema validation is enabled
        if self.config.validate_response_schema and validate:
            self.validator = ResponseValidator(self.openapi_spec)
        
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
        
        status_code, response_time, error, response_body = self.client.call_endpoint_with_response(
            method, resolved_path, query_params, 
            request_body if request_body else None
        )
        
        success = self._determine_success(error, status_code, method)
        if status_code == 500 and not error:
            error = "Internal Server Error"
        
        # Validate response schema
        schema_valid = None
        schema_error = None
        if self.validator and response_body and success:
            schema_valid, schema_error = self.validator.validate_response(
                operation, status_code, response_body
            )
            if not schema_valid:
                success = False
        
        if self.config.verbose:
            status_str = "✓" if success else "✗"
            logger.info(f"{status_str} {method} {resolved_path} -> {status_code} ({response_time:.2f}s)")
        
        return EndpointResult(
            path=path,
            method=method,
            status_code=status_code,
            success=success,
            response_time=response_time,
            error=error,
            schema_valid=schema_valid,
            schema_error=schema_error
        )
    
    def run(self) -> TestSummary:
        if not self.setup():
            return self.summary
        
        endpoints = self.get_endpoints()
        logger.info(f"Found {len(endpoints)} endpoints to test")
        logger.info(f"Running with {self.config.max_workers} parallel workers")
        
        # Parallel execution
        with ThreadPoolExecutor(max_workers=self.config.max_workers) as executor:
            futures = {
                executor.submit(self.test_endpoint, method, path, operation): (method, path)
                for method, path, operation in endpoints
            }
            
            for future in as_completed(futures):
                method, path = futures[future]
                try:
                    result = future.result()
                    with self.lock:
                        self.summary.results.append(result)
                        self.summary.total += 1
                        if result.skipped:
                            self.summary.skipped += 1
                        elif result.success:
                            self.summary.passed += 1
                        else:
                            self.summary.failed += 1
                except Exception as e:
                    logger.error(f"Error testing {method} {path}: {e}")
        
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
                if result.schema_error:
                    error_msg += f" (Schema: {result.schema_error})"
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
        
        # Generate reports
        self._generate_reports()
        
        return self.summary.failed == 0
    
    def _generate_reports(self):
        """Generate test reports in configured formats"""
        formats = self.config.output_format.split(',')
        
        for fmt in formats:
            fmt = fmt.strip()
            if fmt == "json" or fmt == "all":
                ReportGenerator.generate_json_report(
                    self.summary, self.config,
                    f"{self.config.output_file}.json"
                )
            if fmt == "html" or fmt == "all":
                ReportGenerator.generate_html_report(
                    self.summary, self.config,
                    f"{self.config.output_file}.html"
                )


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
    parser.add_argument(
        "--max-workers",
        type=int,
        default=10,
        help="Maximum number of parallel workers (default: 10)"
    )
    parser.add_argument(
        "--output-format",
        default="console",
        choices=["console", "json", "html", "all"],
        help="Output format for test reports (default: console)"
    )
    parser.add_argument(
        "--output-file",
        default="test_report",
        help="Base filename for test reports (default: test_report)"
    )
    parser.add_argument(
        "--no-schema-validation",
        action="store_true",
        help="Disable response schema validation"
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
        max_workers=args.max_workers,
        output_format=args.output_format,
        output_file=args.output_file,
        validate_response_schema=not args.no_schema_validation,
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
