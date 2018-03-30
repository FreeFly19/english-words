# --- !Ups
create sequence phrases_id_seq;
create table "phrases" (
  "id" BIGSERIAL NOT NULL PRIMARY KEY,
  "text" VARCHAR NOT NULL,
  "created_at" TIMESTAMP NOT NULL
);
create table "translations" (
  "id" BIGSERIAL NOT NULL PRIMARY KEY,
  "phrase_id" BIGINT NOT NULL,
  "picture" VARCHAR NOT NULL,
  "value" VARCHAR NOT NULL,
  "votes" BIGINT NOT NULL
);
alter table "translations"
  add constraint "phrase" foreign key("phrase_id") references "phrases"("id")
  on update NO ACTION on delete NO ACTION;


# --- !Downs
alter table "translations" drop constraint "phrase";
drop table "translations";
drop table "phrases";
drop sequence "phrases_id_seq";