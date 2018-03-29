# --- !Ups
create sequence phrases_id_seq;
create table phrases(
  id bigint not null default nextval('phrases_id_seq'),
  text text, primary key(id)
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