-- 1. Create tables
CREATE TABLE public.vendors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    category TEXT,
    distance TEXT,
    rating TEXT DEFAULT '0.0',
    review_count INTEGER DEFAULT 0,
    is_online BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE TABLE public.products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id UUID REFERENCES public.vendors(id) ON DELETE CASCADE NOT NULL,
    name TEXT NOT NULL,
    price DECIMAL(10,2) DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 2. Enable Row Level Security
ALTER TABLE public.vendors ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;

-- 3. Create policies for vendors
-- Anyone can view online vendors
CREATE POLICY "Vendors are viewable by everyone" ON public.vendors
    FOR SELECT USING (true);

-- Vendors can update their own profiles
CREATE POLICY "Vendors can update own profile" ON public.vendors
    FOR UPDATE USING (auth.uid() = user_id);

-- Vendors can insert their own profiles
CREATE POLICY "Vendors can insert own profile" ON public.vendors
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- 4. Create policies for products
-- Anyone can view products
CREATE POLICY "Products are viewable by everyone" ON public.products
    FOR SELECT USING (true);

-- Vendors can manage their own products
CREATE POLICY "Vendors can manage own products" ON public.products
    FOR ALL USING (
        EXISTS (
            SELECT 1 FROM public.vendors
            WHERE vendors.id = products.vendor_id AND vendors.user_id = auth.uid()
        )
    );
