# null-profile Backend

Spring Boot 3.2+ backend application with Java 21 and PostgreSQL.

## Prerequisites

- Docker Desktop with WSL 2 integration enabled
- Maven (for initial setup only)

## Initial Setup

### 1. Generate Maven Wrapper

If you haven't already, generate the Maven wrapper (needed for Docker build):

```bash
cd /mnt/c/Users/luis.ribeiro/Documents/sources/null-profile/null-profile-be/null-profile-be
mvn wrapper:wrapper
```

### 2. Configure Environment (Optional)

Copy the example environment file and customize if needed:

```bash
cp .env.example .env
```

Edit `.env` to change default values like database credentials, ports, etc.

## Running with Docker Compose

### Start the Application

From WSL, navigate to the backend directory and run:

```bash
cd /mnt/c/Users/luis.ribeiro/Documents/sources/null-profile/null-profile-be/null-profile-be
docker compose up --build
```

This will:
- Build the Spring Boot application
- Start PostgreSQL database
- Run database migrations with Flyway
- Start the backend service

### Stop the Application

```bash
docker compose down
```

To also remove volumes (database data):

```bash
docker compose down -v
```

## Endpoints

Once running, the application will be available at:

- **API Base**: http://localhost:8080/api
- **Health Check**: http://localhost:8080/actuator/health
- **Example Endpoint**: http://localhost:8080/api/public/hello

## Development

### Viewing Logs

```bash
# All services
docker compose logs -f

# Backend only
docker compose logs -f backend

# PostgreSQL only
docker compose logs -f postgres
```

### Rebuilding After Code Changes

```bash
docker compose up --build
```

### Accessing the Database

```bash
docker exec -it nullprofile-postgres psql -U profile_user -d profile
```

## Configuration

The application uses environment variables for configuration. Default values are:

- **Server Port**: 8080
- **Database**: profile
- **Database User**: profile_user
- **Database Password**: profile_pass
- **Database Port**: 5432

See `.env.example` for all available configuration options.

## Package Structure

```
ch.nullprofile
├── Application.java           # Main Spring Boot application
└── controller
    └── PublicController.java  # Example REST controller
```

## Billing Setup (Stripe)

The application includes Stripe billing integration for donations and future subscription support.

### Prerequisites

1. **Stripe Account**: Sign up at [https://stripe.com](https://stripe.com)
2. **Stripe API Keys**: Get test keys from [https://dashboard.stripe.com/test/apikeys](https://dashboard.stripe.com/test/apikeys)
3. **Webhook Secret**: Configure webhook endpoint in Stripe Dashboard

### Configuration

1. **Update environment variables** in `.env.local`:
   ```bash
   STRIPE_SECRET_KEY=sk_test_...
   STRIPE_PUBLISHABLE_KEY=pk_test_...
   STRIPE_WEBHOOK_SECRET=whsec_...
   BILLING_MODE=donation
   BILLING_CURRENCY=EUR
   BILLING_SUCCESS_URL=http://localhost:8080/billing/success
   BILLING_CANCEL_URL=http://localhost:8080/billing/cancel
   ```

2. **Configure Stripe Webhook** (for local development):
   - Use [Stripe CLI](https://stripe.com/docs/stripe-cli) to forward webhooks:
     ```bash
     stripe listen --forward-to localhost:8080/api/billing/webhook
     ```
   - Copy the webhook signing secret (`whsec_...`) to `STRIPE_WEBHOOK_SECRET`

3. **Restart application** to apply changes:
   ```bash
   docker compose down
   docker compose up --build
   ```

### Billing Modes

- **donation**: One-time donations via Stripe Checkout (default)
- **subscription**: Recurring subscriptions (future support)
- **disabled**: Billing completely disabled

### API Endpoints

- `POST /api/billing/donations/checkout-session` - Create donation checkout session
  ```json
  {
    "userId": "uuid",
    "amount": 500
  }
  ```
  Response: `{ "url": "https://checkout.stripe.com/..." }`

- `POST /api/billing/webhook` - Stripe webhook endpoint (for Stripe only)

### Database Tables

The billing module uses isolated tables in the `public` schema:
- `billing_customers` - Maps users to Stripe customers
- `billing_subscriptions` - Tracks subscriptions (future)
- `billing_payments` - Records payment transactions
- `billing_events` - Webhook event log for idempotency

See migration `V4__billing_stripe_tables.sql` for complete schema.

### Testing

1. **Create test donation**:
   ```bash
   curl -X POST http://localhost:8080/api/billing/donations/checkout-session \
     -H "Content-Type: application/json" \
     -d '{"userId": "your-user-uuid", "amount": 1000}'
   ```

2. **Open returned checkout URL** in browser

3. **Use Stripe test cards**:
   - Success: `4242 4242 4242 4242`
   - Decline: `4000 0000 0000 0002`
   - [More test cards](https://stripe.com/docs/testing)

4. **Verify webhook received**:
   ```bash
   docker compose logs -f backend | grep "webhook"
   ```

## Database Migrations

Flyway migrations should be placed in:
```
src/main/resources/db/migration/
```

Example naming: `V1__initial_schema.sql`
