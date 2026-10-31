-- Run in HireMyDriver Photos > SQL Editor.
-- Requires the existing Firebase third-party provider for hiremydriver-e88f7.
-- Storage setup only: Android registration and trusted admin review still need integration.
-- Upload contract: <Firebase UID>/<submission UUID>/<document filename>.
-- Use new submission UUIDs for new attempts; never overwrite submitted documents.
begin;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('driver-documents', 'driver-documents', false, 5242880,
        array['image/jpeg', 'image/png'])
on conflict (id) do update set public = false, file_size_limit = 5242880,
    allowed_mime_types = array['image/jpeg', 'image/png'];

-- Standard Firebase tokens may use the anon Postgres role. Access still requires
-- a verified, non-anonymous Firebase token from this exact project, and its own UID.
-- Firebase UIDs are strings; do not cast them to UUID via auth.uid().
create or replace function public.owns_hmd_driver_document(bucket text, object_name text)
returns boolean language sql stable security invoker set search_path = ''
as $$
    select coalesce(
        bucket = 'driver-documents'
        and (auth.jwt()->>'iss') = 'https://securetoken.google.com/hiremydriver-e88f7'
        and (auth.jwt()->>'aud') = 'hiremydriver-e88f7'
        and (auth.jwt()->>'sub') ~ '^[A-Za-z0-9_-]{1,128}$'
        and (auth.jwt()->'firebase'->>'sign_in_provider') is not null
        and (auth.jwt()->'firebase'->>'sign_in_provider') <> 'anonymous'
        and split_part(object_name, '/', 1) = (auth.jwt()->>'sub')
        and object_name ~ '^[A-Za-z0-9_-]{1,128}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/(license-front|license-back|police-clearance)\.(jpg|png)$'
    , false);
$$;
revoke all on function public.owns_hmd_driver_document(text, text) from public;
grant execute on function public.owns_hmd_driver_document(text, text) to anon, authenticated;

-- Scope every policy to this bucket so existing customer profile photos keep working.
drop policy if exists "hmd_driver_document_boundary" on storage.objects;
create policy "hmd_driver_document_boundary" on storage.objects
as restrictive for all to anon, authenticated
using (bucket_id <> 'driver-documents' or public.owns_hmd_driver_document(bucket_id, name))
with check (bucket_id <> 'driver-documents' or public.owns_hmd_driver_document(bucket_id, name));

drop policy if exists "hmd_driver_document_read" on storage.objects;
create policy "hmd_driver_document_read" on storage.objects for select to anon, authenticated
using (public.owns_hmd_driver_document(bucket_id, name));

drop policy if exists "hmd_driver_document_insert" on storage.objects;
create policy "hmd_driver_document_insert" on storage.objects for insert to anon, authenticated
with check (public.owns_hmd_driver_document(bucket_id, name));

-- Even if another permissive policy exists, clients cannot replace/delete submitted files.
drop policy if exists "hmd_driver_document_no_update" on storage.objects;
create policy "hmd_driver_document_no_update" on storage.objects
as restrictive for update to anon, authenticated
using (bucket_id <> 'driver-documents')
with check (bucket_id <> 'driver-documents');

drop policy if exists "hmd_driver_document_no_delete" on storage.objects;
create policy "hmd_driver_document_no_delete" on storage.objects
as restrictive for delete to anon, authenticated
using (bucket_id <> 'driver-documents');

commit;
